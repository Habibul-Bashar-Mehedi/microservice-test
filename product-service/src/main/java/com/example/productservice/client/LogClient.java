package com.example.productservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class LogClient {

    private static final String SERVICE_NAME = "product-service";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LogClient(@Value("${log-service.base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void recordPublish(String routingKey, Object payload) {
        record("PUBLISHED", routingKey, null, payload, "SUCCESS", null);
    }

    public void recordPublishFailed(String routingKey, Object payload, String detail) {
        record("PUBLISHED", routingKey, null, payload, "FAILED", detail);
    }

    public void recordConsume(String routingKey, String queue, Object payload) {
        record("CONSUMED", routingKey, queue, payload, "SUCCESS", null);
    }

    public void recordConsumeFailed(String routingKey, String queue, Object payload, String detail) {
        record("CONSUMED", routingKey, queue, payload, "FAILED", detail);
    }

    private void record(String direction, String routingKey, String queue, Object payload, String status, String detail) {
        try {
            restClient.post()
                    .uri("/v1/logs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LogEntry(
                            SERVICE_NAME,
                            direction,
                            routingKey,
                            queue,
                            toJson(payload),
                            status,
                            detail
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to write message log for routing key {}: {}", routingKey, e.getMessage());
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            return String.valueOf(payload);
        }
    }

    private record LogEntry(
            String serviceName,
            String direction,
            String routingKey,
            String queue,
            String payload,
            String status,
            String detail
    ) {
    }
}