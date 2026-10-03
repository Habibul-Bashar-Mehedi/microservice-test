package com.example.logservice.service;

import com.example.logservice.entity.MessageLog;
import com.example.logservice.entity.MessageLogDocument;
import com.example.logservice.repository.MessageLogRepository;
import com.example.logservice.repository.MessageLogSearchRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageLogSearchService implements ApplicationRunner {

    private static final List<String> SEARCH_FIELDS = List.of(
            "serviceName", "direction", "routingKey", "queue", "email", "payload", "status", "detail"
    );

    private final MessageLogSearchRepository searchRepository;
    private final ElasticsearchOperations operations;
    private final MessageLogRepository messageLogRepository;

    public List<MessageLogDocument> search(String query) {
        String pattern = "*" + query + "*";
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    for (String field : SEARCH_FIELDS) {
                        b.should(s -> s.wildcard(w -> w.field(field).value(pattern).caseInsensitive(true)));
                    }
                    return b;
                }))
                .build();
        return operations.search(nativeQuery, MessageLogDocument.class)
                .stream()
                .map(SearchHit::getContent)
                .toList();
    }

    public void index(MessageLog logEntry) {
        try {
            searchRepository.save(MessageLogDocument.from(logEntry));
        } catch (Exception e) {
            log.warn("Failed to index message log {} in Elasticsearch: {}", logEntry.getId(), e.getMessage());
        }
    }

    public void deleteAll() {
        try {
            searchRepository.deleteAll();
        } catch (Exception e) {
            log.warn("Failed to delete message logs from Elasticsearch: {}", e.getMessage());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            IndexOperations indexOps = operations.indexOps(MessageLogDocument.class);
            if (indexOps.exists()) {
                indexOps.delete();
            }
            indexOps.create();
            indexOps.putMapping();

            List<MessageLog> logs = messageLogRepository.findAll();
            searchRepository.saveAll(logs.stream().map(MessageLogDocument::from).toList());
            log.info("Reindexed {} message logs into Elasticsearch", logs.size());
        } catch (Exception e) {
            log.warn("Elasticsearch reindex failed: {}", e.getMessage());
        }
    }
}