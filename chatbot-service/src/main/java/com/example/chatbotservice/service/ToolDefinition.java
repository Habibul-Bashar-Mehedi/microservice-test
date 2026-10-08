package com.example.chatbotservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters,
        Set<String> roles,
        boolean requiresConfirmation,
        BiFunction<ToolContext, JsonNode, Object> handler) {

    public boolean allows(String role) {
        return roles.contains(role);
    }

    public Map<String, Object> toLlmSpec() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters));
    }
}
