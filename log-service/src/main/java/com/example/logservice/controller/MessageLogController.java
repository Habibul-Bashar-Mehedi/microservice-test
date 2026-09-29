package com.example.logservice.controller;

import com.example.logservice.entity.MessageLog;
import com.example.logservice.service.MessageLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/logs")
@RequiredArgsConstructor
public class MessageLogController {

    private final MessageLogService messageLogService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageLog record(@Valid @RequestBody LogEntryRequest request) {
        MessageLog entry = MessageLog.builder()
                .serviceName(request.serviceName())
                .direction(request.direction())
                .routingKey(request.routingKey())
                .queue(request.queue())
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

    public record LogEntryRequest(
            @NotBlank String serviceName,
            @NotBlank String direction,
            @NotBlank String routingKey,
            String queue,
            String payload,
            @NotBlank String status,
            String detail
    ) {
    }
}