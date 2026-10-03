package com.example.logservice.controller;

import com.example.logservice.entity.MessageLog;
import com.example.logservice.entity.MessageLogDocument;
import com.example.logservice.service.MessageLogSearchService;
import com.example.logservice.service.MessageLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/logs")
@RequiredArgsConstructor
public class MessageLogController {

    private final MessageLogService messageLogService;
    private final MessageLogSearchService messageLogSearchService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageLog record(@Valid @RequestBody LogEntryRequest request) {
        MessageLog entry = MessageLog.builder()
                .serviceName(request.serviceName())
                .direction(request.direction())
                .routingKey(request.routingKey())
                .queue(request.queue())
                .email(request.email())
                .payload(request.payload())
                .status(request.status())
                .detail(request.detail())
                .createdAt(LocalDateTime.now())
                .build();
        return messageLogService.record(entry);
    }

    @GetMapping
    public List<MessageLog> list() {
        return messageLogService.findAll();
    }

    @GetMapping("/search")
    public List<MessageLogDocument> search(@RequestParam(name = "q", defaultValue = "") String q) {
        if (q == null || q.isBlank()) {
            return messageLogSearchService.search(" ");
        }
        return messageLogSearchService.search(q.trim());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAll() {
        messageLogService.deleteAll();
    }

    public record LogEntryRequest(
            @NotBlank String serviceName,
            @NotBlank String direction,
            @NotBlank String routingKey,
            String queue,
            String email,
            String payload,
            @NotBlank String status,
            String detail
    ) {
    }
}