package com.example.chatbotservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record PendingAction(
        String confirmationId,
        List<Action> actions,
        String summary) {

    public record Action(String toolName, JsonNode arguments) {
    }
}