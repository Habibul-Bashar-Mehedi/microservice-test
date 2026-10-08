package com.example.chatbotservice.config;

import com.example.chatbotservice.llm.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public LlmClient llmClient(LlmProperties properties, ObjectMapper objectMapper) {
        return new LlmClient(properties, objectMapper);
    }
}
