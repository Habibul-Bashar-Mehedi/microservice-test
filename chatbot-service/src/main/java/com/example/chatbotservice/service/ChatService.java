package com.example.chatbotservice.service;

import com.example.chatbotservice.config.LlmProperties;
import com.example.chatbotservice.dto.ChatRequest;
import com.example.chatbotservice.dto.ChatResponse;
import com.example.chatbotservice.dto.ConfirmRequest;
import com.example.chatbotservice.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    private final LlmClient llm;
    private final ToolRegistry registry;
    private final ConversationStore store;
    private final ObjectMapper mapper;
    private final int maxIterations;

    public ChatService(LlmClient llm, ToolRegistry registry, ConversationStore store,
            ObjectMapper mapper, LlmProperties properties) {
        this.llm = llm;
        this.registry = registry;
        this.store = store;
        this.mapper = mapper;
        this.maxIterations = Math.max(1, properties.maxToolIterations());
    }

    public ChatResponse chat(String email, String role, String authHeader, ChatRequest request) {
        Conversation conversation = store.getOrCreate(request.conversationId(), email, role);
        if (conversation.messages().isEmpty()) {
            conversation.addMessage(Map.of("role", "system", "content", systemPrompt(role)));
        }
        conversation.addMessage(Map.of("role", "user", "content", request.message()));

        ToolContext context = new ToolContext(email, role, authHeader);

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            JsonNode assistant = llm.complete(conversation.messages(), registry.llmSpecsFor(role),
                    conversation.id());
            String content = assistant.path("content").isNull() ? "" : assistant.path("content").asText("");
            JsonNode toolCalls = assistant.path("tool_calls");

            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                conversation.addMessage(Map.of("role", "assistant", "content", content));
                return ChatResponse.reply(conversation.id(),
                        content.isBlank() ? "I'm not sure how to help with that." : content);
            }

            List<JsonNode> confirmingCalls = confirmingCalls(role, toolCalls);
            if (!confirmingCalls.isEmpty()) {
                List<PendingAction.Action> actions = new java.util.ArrayList<>();
                List<String> summaries = new java.util.ArrayList<>();
                for (JsonNode call : confirmingCalls) {
                    String name = call.path("function").path("name").asText();
                    JsonNode arguments = parseArguments(call.path("function").path("arguments").asText());
                    actions.add(new PendingAction.Action(name, arguments));
                    summaries.add(summarize(name, arguments));
                }
                String summary = String.join("; ", summaries);
                String confirmationId = UUID.randomUUID().toString();
                conversation.setPending(new PendingAction(confirmationId, actions, summary));
                String reply = content.isBlank() ? "Please confirm: " + summary + "." : content;
                conversation.addMessage(Map.of("role", "assistant", "content", reply));
                return ChatResponse.confirmation(conversation.id(), reply, confirmationId, summary);
            }

            conversation.addMessage(assistantToolCallMessage(assistant));
            for (JsonNode call : toolCalls) {
                conversation.addMessage(toolResultMessage(call, context, role));
            }
        }

        return ChatResponse.reply(conversation.id(),
                "I couldn't finish that request. Please try rephrasing it.");
    }

    public ChatResponse confirm(String email, String role, String authHeader, ConfirmRequest request) {
        Conversation conversation = store.get(request.conversationId());
        if (conversation == null || conversation.pending() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No action is awaiting confirmation");
        }
        if (!conversation.email().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This conversation belongs to another user");
        }
        PendingAction pending = conversation.pending();
        if (!pending.confirmationId().equals(request.confirmationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirmation id does not match");
        }

        conversation.setPending(null);

        List<Map<String, Object>> executed = new java.util.ArrayList<>();
        for (PendingAction.Action action : pending.actions()) {
            ToolDefinition tool = registry.find(role, action.toolName())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "Your role is not allowed to perform this action"));
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("action", action.toolName());
            entry.put("arguments", action.arguments());
            try {
                entry.put("result", tool.handler().apply(new ToolContext(email, role, authHeader),
                        action.arguments()));
            } catch (Exception e) {
                entry.put("error", rootMessage(e));
            }
            executed.add(entry);
        }

        boolean anyFailure = executed.stream().anyMatch(entry -> entry.containsKey("error"));
        String resultJson = write(executed);
        conversation.addMessage(Map.of("role", "system",
                "content", (anyFailure ? "Some actions failed" : "Actions completed successfully")
                        + ". Results: " + resultJson));

        try {
            JsonNode assistant = llm.complete(conversation.messages(), List.of(), conversation.id());
            String content = assistant.path("content").isNull() ? "" : assistant.path("content").asText("");
            if (!content.isBlank()) {
                conversation.addMessage(Map.of("role", "assistant", "content", content));
                return ChatResponse.reply(conversation.id(), content);
            }
        } catch (RuntimeException ignored) {
            // fall through to a deterministic confirmation
        }
        return ChatResponse.reply(conversation.id(),
                anyFailure ? "I completed what I could, but some actions failed." : "Done. " + pending.summary() + ".");
    }

    public ChatResponse cancel(String email, String conversationId) {
        Conversation conversation = store.get(conversationId);
        if (conversation != null && conversation.email().equalsIgnoreCase(email)) {
            conversation.setPending(null);
        }
        return ChatResponse.reply(conversationId, "Okay, cancelled.");
    }

    // ---- helpers -----------------------------------------------------------

    private List<JsonNode> confirmingCalls(String role, JsonNode toolCalls) {
        List<JsonNode> confirming = new java.util.ArrayList<>();
        for (JsonNode call : toolCalls) {
            String name = call.path("function").path("name").asText();
            ToolDefinition tool = registry.find(role, name).orElse(null);
            if (tool != null && tool.requiresConfirmation()) {
                confirming.add(call);
            }
        }
        return confirming;
    }

    private Map<String, Object> toolResultMessage(JsonNode call, ToolContext context, String role) {
        String id = call.path("id").asText();
        String name = call.path("function").path("name").asText();
        JsonNode arguments = parseArguments(call.path("function").path("arguments").asText());

        ToolDefinition tool = registry.find(role, name).orElse(null);
        String result;
        if (tool == null) {
            result = "{\"error\":\"Tool '" + name + "' is not available for your role\"}";
        } else {
            try {
                result = write(tool.handler().apply(context, arguments));
            } catch (Exception e) {
                result = "{\"error\":" + write(rootMessage(e)) + "}";
            }
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", id);
        message.put("content", result);
        return message;
    }

    private Map<String, Object> assistantToolCallMessage(JsonNode assistant) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", assistant.path("content").isNull() ? null : assistant.path("content").asText());
        message.put("tool_calls", mapper.convertValue(assistant.path("tool_calls"), Object.class));
        return message;
    }

    private JsonNode parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String summarize(String toolName, JsonNode args) {
        return switch (toolName) {
            case "create_product", "resubmit_product" -> "create product '" + args.path("name").asText()
                    + "' priced " + args.path("price").asText()
                    + " with quantity " + args.path("availableQuantity").asText();
            case "create_order" -> "place an order for " + args.path("items").toString();
            case "approve_product" -> "approve product #" + args.path("id").asText();
            case "reject_product" -> "reject product #" + args.path("id").asText()
                    + " (reason: " + args.path("reason").asText() + ")";
            case "confirm_order" -> "confirm order #" + args.path("id").asText();
            case "cancel_order" -> "cancel order #" + args.path("id").asText();
            case "checkout_cart" -> "create orders for all items in the cart and empty it";
            case "update_product_name" -> "update product #" + args.path("id").asText()
                    + " name to '" + args.path("name").asText() + "'";
            case "update_product_price" -> "update product #" + args.path("id").asText()
                    + " price to " + args.path("price").asText();
            case "add_stock" -> "add " + args.path("quantity").asText()
                    + " stock to product #" + args.path("id").asText();
            case "change_user_role" -> "change the role of user "
                    + (args.hasNonNull("userId") ? args.path("userId").asText() : args.path("email").asText())
                    + " to " + args.path("role").asText();
            default -> toolName + " " + args.toString();
        };
    }

    private String systemPrompt(String role) {
        String capabilities = switch (role) {
            case "USER" -> "You help a general (customer) user browse products, manage a cart, place orders, "
                    + "view their orders, and cancel their own pending orders.";
            case "MAINTAINER" -> "You help the maintainer create products, correct/resubmit rejected products, "
                    + "view and search the product list, read notifications/messages, and view and confirm orders.";
            case "MANAGER" -> "You help the manager review pending products, approve or reject them with a reason, "
                    + "view and search the product list, and read notifications/messages.";
            case "PRODUCT_SPECIALIST" -> "You help the product specialist review pending products, approve or reject "
                    + "them with a reason, view and search the product list, and read notifications/messages.";
            case "SALESMAN" -> "You help the salesman review pending products, approve or reject them with a reason, "
                    + "view and search the product list, and read notifications/messages.";
            case "ADMIN" -> "You help the admin do final product review, approve or reject products, create products, "
                    + "view and search the product list, manage users and roles, view logs, and view and confirm orders.";
            default -> "You help the user.";
        };

        return "You are the assistant for the " + role + " role in a product management system. "
                + capabilities + "\n"
                + "Strict rules:\n"
                + "1. You may ONLY use the tools provided to you. Never claim to perform an action that is not backed by a tool.\n"
                + "2. If the user asks for something outside your role's permissions, politely refuse and tell them "
                + "they do not have permission for it. Do not attempt it.\n"
                + "3. When the user asks you to perform an action that changes data (create/resubmit a product, approve or "
                + "reject a product, confirm an order, create an order, change a user's role), you MUST call the matching "
                + "tool with the full details right away, never merely describe the action in text. The system will then "
                + "ask the user to confirm before anything is executed, so do not wait for a second prompt and do not say "
                + "the action is done or pending until after it is executed.\n"
                + "4. Use tools to look up real data. Never invent product ids, prices, orders, users or logs.\n"
                + "5. Users manage a cart: use add_to_cart to add products, view_cart to show it, "
                + "update_cart_quantity / remove_from_cart / clear_cart to change it, and checkout_cart to place the "
                + "orders. When the user names products, first call search_products to find the product ids.\n"
                + "6. Keep replies short and clear.";
    }
}
