package com.example.chatbotservice.llm;

import com.example.chatbotservice.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Minimal client for the OpenCode Go OpenAI-compatible Chat Completions API.
 * Supports function/tool calling. A stable session id is required per conversation.
 */
public class LlmClient {

    private final RestClient client;
    private final ObjectMapper mapper;
    private final LlmProperties properties;

    public LlmClient(LlmProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(90_000);
        this.client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader("User-Agent", "microservice-chatbot/1.0")
                .build();
    }

    public JsonNode complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools, String session) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "LLM API key is not configured (set OPENCODE_API_KEY)");
        }
        if (properties.model() == null || properties.model().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "LLM model is not configured (llm.model)");
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", properties.model());
        body.set("messages", mapper.valueToTree(messages));
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsNode = mapper.valueToTree(tools);
            body.set("tools", toolsNode);
            body.put("tool_choice", "auto");
        }
        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to serialize LLM request", e);
        }
        try {
            String responseBody = client.post()
                    .uri("/chat/completions")
                    .header("x-opencode-session", session == null || session.isBlank() ? "default" : session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);

            JsonNode response = mapper.readTree(responseBody);
            JsonNode choice = response == null ? null : response.path("choices").path(0);
            JsonNode message = choice == null ? null : choice.path("message");
            if (message == null || message.isMissingNode() || message.isNull()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "LLM returned no message");
            }
            return message;
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "LLM request failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Failed to parse LLM response", e);
        }
    }
}
