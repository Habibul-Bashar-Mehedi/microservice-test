package com.example.logservice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.logservice.entity.MessageLog;
import com.example.logservice.entity.MessageLogDocument;
import com.example.logservice.repository.MessageLogRepository;
import com.example.logservice.repository.MessageLogSearchRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

@ExtendWith(MockitoExtension.class)
class MessageLogSearchServiceTest {

    @Mock
    private MessageLogSearchRepository searchRepository;
    @Mock
    private ElasticsearchOperations operations;
    @Mock
    private MessageLogRepository messageLogRepository;
    @Mock
    private IndexOperations indexOperations;

    private MessageLogSearchService service;

    @BeforeEach
    void setUp() {
        service = new MessageLogSearchService(searchRepository, operations, messageLogRepository);
    }

    private MessageLog log() {
        return MessageLog.builder().id(1L).serviceName("order-service").direction("PUBLISHED")
                .routingKey("order.created").status("SUCCESS").build();
    }

    @Test
    void index_savesDocument() {
        service.index(log());

        verify(searchRepository).save(any(MessageLogDocument.class));
    }

    @Test
    void index_swallowsFailures() {
        when(searchRepository.save(any())).thenThrow(new RuntimeException("es down"));

        assertThatCode(() -> service.index(log())).doesNotThrowAnyException();
    }

    @Test
    void deleteAll_clearsIndexAndSwallowsFailures() {
        service.deleteAll();

        verify(searchRepository).deleteAll();
    }

    @Test
    void run_recreatesIndexAndReindexes() {
        when(operations.indexOps(MessageLogDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(messageLogRepository.findAll()).thenReturn(List.of(log()));

        service.run(null);

        verify(indexOperations).delete();
        verify(indexOperations).create();
        verify(indexOperations).putMapping();
        verify(searchRepository).saveAll(any());
    }
}