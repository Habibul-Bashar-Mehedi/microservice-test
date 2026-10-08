package com.example.chatbotservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String baseUrl,
        String apiKey,
        String model,
        int maxToolIterations) {
}
