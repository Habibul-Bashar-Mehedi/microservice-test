package com.example.logservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.logservice.entity.MessageLog;
import com.example.logservice.repository.MessageLogRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageLogServiceTest {

    @Mock
    private MessageLogRepository messageLogRepository;
    @Mock
    private MessageLogSearchService messageLogSearchService;

    private MessageLogService messageLogService;

    @BeforeEach
    void setUp() {
        messageLogService = new MessageLogService(messageLogRepository, messageLogSearchService);
    }

    @Test
    void record_stampsCreatedAtAndIndexes() {
        when(messageLogRepository.save(any(MessageLog.class))).thenAnswer(inv -> inv.getArgument(0));
        MessageLog entry = MessageLog.builder()
                .serviceName("order-service")
                .direction("PUBLISHED")
                .routingKey("order.created")
                .status("SUCCESS")
                .build();

        MessageLog saved = messageLogService.record(entry);

        assertThat(saved.getCreatedAt()).isNotNull();
        verify(messageLogSearchService).index(saved);
    }

    @Test
    void findAll_delegatesToRepository() {
        List<MessageLog> logs = List.of(MessageLog.builder().id(1L).build());
        when(messageLogRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(logs);

        assertThat(messageLogService.findAll()).isEqualTo(logs);
    }

    @Test
    void deleteAll_clearsDatabaseAndIndex() {
        messageLogService.deleteAll();

        verify(messageLogRepository).deleteAll();
        verify(messageLogSearchService).deleteAll();
    }
}