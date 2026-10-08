package com.example.chatbotservice.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConversationStore {

    private final ConcurrentHashMap<String, Conversation> conversations = new ConcurrentHashMap<>();

    public Conversation getOrCreate(String conversationId, String email, String role) {
        String id = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString()
                : conversationId;
        return conversations.compute(id, (key, existing) -> {
            if (existing == null || !existing.email().equalsIgnoreCase(email)) {
                return new Conversation(id, email, role);
            }
            return existing;
        });
    }

    public Conversation get(String conversationId) {
        return conversationId == null ? null : conversations.get(conversationId);
    }
}
