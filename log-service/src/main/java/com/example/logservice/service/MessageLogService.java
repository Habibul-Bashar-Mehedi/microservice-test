package com.example.logservice.service;

import com.example.logservice.entity.MessageLog;
import com.example.logservice.repository.MessageLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageLogService {

    private final MessageLogRepository messageLogRepository;
    private final MessageLogSearchService messageLogSearchService;

    @Transactional
    public MessageLog record(MessageLog entry) {
        entry.setCreatedAt(LocalDateTime.now());
        MessageLog saved = messageLogRepository.save(entry);
        messageLogSearchService.index(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MessageLog> findAll() {
        return messageLogRepository.findAllByOrderByCreatedAtDescIdDesc();
    }

    @Transactional
    public void deleteAll() {
        messageLogRepository.deleteAll();
        messageLogSearchService.deleteAll();
    }
}