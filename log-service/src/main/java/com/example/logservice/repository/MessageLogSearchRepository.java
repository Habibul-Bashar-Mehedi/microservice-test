package com.example.logservice.repository;

import com.example.logservice.entity.MessageLogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface MessageLogSearchRepository extends ElasticsearchRepository<MessageLogDocument, Long> {
}