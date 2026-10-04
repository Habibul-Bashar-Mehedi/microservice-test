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

    public LogClient(RestClient.Builder restClientBuilder, @Value("${log-service.base-url}") String baseUrl,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public void recordPublish(String routingKey, Object payload) {
        record("PUBLISHED", routingKey, null, payload, "SUCCESS", null, null);
    }

    public void recordPublish(String routingKey, Object payload, String email) {
        record("PUBLISHED", routingKey, null, payload, "SUCCESS", null, email);
    }

    public void recordPublishFailed(String routingKey, Object payload, String detail) {
        record("PUBLISHED", routingKey, null, payload, "FAILED", detail, null);
    }

    public void recordConsume(String routingKey, String queue, Object payload) {
        record("CONSUMED", routingKey, queue, payload, "SUCCESS", null, null);
    }

    public void recordConsume(String routingKey, String queue, Object payload, String email) {
        record("CONSUMED", routingKey, queue, payload, "SUCCESS", null, email);
    }

    public void recordConsumeFailed(String routingKey, String queue, Object payload, String detail) {
        record("CONSUMED", routingKey, queue, payload, "FAILED", detail, null);
    }

    public void recordConsumeFailed(String routingKey, String queue, Object payload, String detail, String email) {
        record("CONSUMED", routingKey, queue, payload, "FAILED", detail, email);
    }

    private void record(String direction, String routingKey, String queue, Object payload, String status, String detail,
            String email) {
        try {
            restClient.post()
                    .uri("/v1/logs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LogEntry(
                            SERVICE_NAME,
                            direction,
                            routingKey,
                            queue,
                            email,
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
            String email,
            String payload,
            String status,
            String detail
    ) {
    }
}