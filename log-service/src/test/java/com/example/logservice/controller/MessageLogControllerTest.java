package com.example.logservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.logservice.entity.MessageLog;
import com.example.logservice.entity.MessageLogDocument;
import com.example.logservice.service.MessageLogSearchService;
import com.example.logservice.service.MessageLogService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageLogControllerTest {

    @Mock
    private MessageLogService messageLogService;
    @Mock
    private MessageLogSearchService messageLogSearchService;

    private MessageLogController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageLogController(messageLogService, messageLogSearchService);
    }

    @Test
    void record_buildsEntityAndDelegates() {
        when(messageLogService.record(any(MessageLog.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageLog saved = controller.record(new MessageLogController.LogEntryRequest(
                "order-service", "PUBLISHED", "order.created", null, null, "{}", "SUCCESS", null));

        assertThat(saved.getServiceName()).isEqualTo("order-service");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void list_delegates() {
        when(messageLogService.findAll()).thenReturn(List.of(MessageLog.builder().id(1L).build()));
        assertThat(controller.list()).hasSize(1);
    }

    @Test
    void search_blankUsesWildcard() {
        when(messageLogSearchService.search(" ")).thenReturn(List.of());
        assertThat(controller.search("  ")).isEmpty();
        verify(messageLogSearchService).search(" ");

        MessageLogDocument doc = MessageLogDocument.builder().id(1L).build();
        when(messageLogSearchService.search("phone")).thenReturn(List.of(doc));
        assertThat(controller.search(" phone ")).hasSize(1);
    }

    @Test
    void clearAll_delegates() {
        controller.clearAll();
        verify(messageLogService).deleteAll();
    }
}