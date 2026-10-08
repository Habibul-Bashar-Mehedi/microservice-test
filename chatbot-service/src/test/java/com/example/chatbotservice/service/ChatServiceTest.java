package com.example.chatbotservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chatbotservice.config.LlmProperties;
import com.example.chatbotservice.dto.ChatRequest;
import com.example.chatbotservice.dto.ChatResponse;
import com.example.chatbotservice.dto.ConfirmRequest;
import com.example.chatbotservice.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private LlmClient llm;
    @Mock
    private DownstreamClient downstream;

    private final ObjectMapper mapper = new ObjectMapper();
    private ChatService service;

    @BeforeEach
    void setUp() {
        ToolRegistry registry = new ToolRegistry(downstream, mapper);
        ConversationStore store = new ConversationStore();
        service = new ChatService(llm, registry, store, mapper,
                new LlmProperties("http://llm", "key", "model", 6));
    }

    private JsonNode assistant(String content, String toolCallsJson) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", "assistant");
        if (content == null) {
            node.putNull("content");
        } else {
            node.put("content", content);
        }
        if (toolCallsJson != null) {
            node.set("tool_calls", mapper.readTree(toolCallsJson));
        }
        return node;
    }

    private static final String SEARCH_CALL =
            "[{\"id\":\"call_1\",\"type\":\"function\",\"function\":"
                    + "{\"name\":\"search_products\",\"arguments\":\"{\\\"query\\\":\\\"rice\\\"}\"}}]";

    private static final String CREATE_PRODUCT_CALL =
            "[{\"id\":\"call_2\",\"type\":\"function\",\"function\":"
                    + "{\"name\":\"create_product\",\"arguments\":"
                    + "\"{\\\"name\\\":\\\"Rice\\\",\\\"price\\\":50,\\\"availableQuantity\\\":10}\"}}]";

    @Test
    void chat_withoutTools_returnsReply() throws Exception {
        when(llm.complete(anyList(), anyList(), anyString())).thenReturn(assistant("Hello there", null));

        ChatResponse response = service.chat("u@x.com", "USER", "Bearer t",
                new ChatRequest("hi", null));

        assertThat(response.reply()).isEqualTo("Hello there");
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(response.conversationId()).isNotBlank();
    }

    @Test
    void chat_withReadOnlyTool_executesToolAndLoops() throws Exception {
        when(llm.complete(anyList(), anyList(), anyString()))
                .thenReturn(assistant(null, SEARCH_CALL))
                .thenReturn(assistant("I found Rice", null));
        when(downstream.get(any(), any(), any()))
                .thenReturn(mapper.readTree("[{\"id\":1,\"name\":\"Rice\",\"price\":50}]"));

        ChatResponse response = service.chat("u@x.com", "USER", "Bearer t",
                new ChatRequest("find rice", null));

        assertThat(response.reply()).isEqualTo("I found Rice");
        verify(downstream).get(any(), any(), any());
    }

    @Test
    void chat_withConfirmingTool_requiresConfirmationAndDoesNotExecute() throws Exception {
        when(llm.complete(anyList(), anyList(), anyString())).thenReturn(assistant(null, CREATE_PRODUCT_CALL));

        ChatResponse response = service.chat("m@x.com", "MAINTAINER", "Bearer t",
                new ChatRequest("create rice", "conv-1"));

        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.confirmationId()).isNotBlank();
        assertThat(response.confirmationSummary()).contains("Rice");
        verify(downstream, never()).exchange(any(), any(), any(), any(), any());
    }

    @Test
    void confirm_executesPendingAction() throws Exception {
        when(llm.complete(anyList(), anyList(), anyString())).thenReturn(assistant(null, CREATE_PRODUCT_CALL));
        ChatResponse pending = service.chat("m@x.com", "MAINTAINER", "Bearer t",
                new ChatRequest("create rice", "conv-2"));

        when(downstream.exchange(any(), any(), any(), any(), any()))
                .thenReturn(mapper.readTree("{\"id\":9,\"name\":\"Rice\"}"));
        when(llm.complete(anyList(), anyList(), anyString())).thenReturn(assistant("Product Rice created", null));

        ChatResponse response = service.confirm("m@x.com", "MAINTAINER", "Bearer t",
                new ConfirmRequest(pending.conversationId(), pending.confirmationId()));

        assertThat(response.reply()).isEqualTo("Product Rice created");
        assertThat(response.requiresConfirmation()).isFalse();
        verify(downstream).exchange(any(), any(), any(), any(), any());
    }

    @Test
    void chat_multipleConfirmingActions_batchConfirmExecutesAll() throws Exception {
        String calls = "[{\"id\":\"c1\",\"type\":\"function\",\"function\":"
                + "{\"name\":\"create_product\",\"arguments\":\"{\\\"name\\\":\\\"A\\\",\\\"price\\\":1,\\\"availableQuantity\\\":2}\"}},"
                + "{\"id\":\"c2\",\"type\":\"function\",\"function\":"
                + "{\"name\":\"create_product\",\"arguments\":\"{\\\"name\\\":\\\"B\\\",\\\"price\\\":3,\\\"availableQuantity\\\":4}\"}}]";
        when(llm.complete(anyList(), anyList(), anyString())).thenReturn(assistant(null, calls));

        ChatResponse pending = service.chat("m@x.com", "MAINTAINER", "Bearer t",
                new ChatRequest("add products A and B", "conv-b"));

        assertThat(pending.requiresConfirmation()).isTrue();
        assertThat(pending.confirmationSummary()).contains("'A'").contains("'B'");
        verify(downstream, never()).exchange(any(), any(), any(), any(), any());

        when(downstream.exchange(any(), any(), any(), any(), any()))
                .thenReturn(mapper.readTree("{\"id\":9,\"name\":\"A\"}"));
        when(llm.complete(anyList(), anyList(), anyString())).thenReturn(assistant("Created 2 products", null));

        ChatResponse response = service.confirm("m@x.com", "MAINTAINER", "Bearer t",
                new ConfirmRequest(pending.conversationId(), pending.confirmationId()));

        assertThat(response.reply()).isEqualTo("Created 2 products");
        verify(downstream, times(2)).exchange(any(), any(), any(), any(), any());
    }

    @Test
    void chat_withUnauthorizedTool_reportsErrorInsteadOfExecuting() throws Exception {
        when(llm.complete(anyList(), anyList(), anyString()))
                .thenReturn(assistant(null, CREATE_PRODUCT_CALL))
                .thenReturn(assistant("You do not have permission to do that", null));

        ChatResponse response = service.chat("u@x.com", "USER", "Bearer t",
                new ChatRequest("create a product", null));

        assertThat(response.reply()).contains("permission");
        verify(downstream, never()).exchange(any(), any(), any(), any(), any());
    }
}
