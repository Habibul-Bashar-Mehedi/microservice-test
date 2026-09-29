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

    @Transactional
    public MessageLog record(MessageLog entry) {
        entry.setCreatedAt(LocalDateTime.now());
        return messageLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<MessageLog> findAll() {
        return messageLogRepository.findAllByOrderByCreatedAtDescIdDesc();
    }
}