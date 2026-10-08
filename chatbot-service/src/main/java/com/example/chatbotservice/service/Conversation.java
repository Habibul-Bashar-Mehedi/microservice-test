package com.example.chatbotservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Conversation {

    private final String id;
    private final String email;
    private final String role;
    private final List<Map<String, Object>> messages = new ArrayList<>();
    private PendingAction pending;

    public Conversation(String id, String email, String role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public String id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String role() {
        return role;
    }

    public List<Map<String, Object>> messages() {
        return messages;
    }

    public void addMessage(Map<String, Object> message) {
        messages.add(message);
    }

    public PendingAction pending() {
        return pending;
    }

    public void setPending(PendingAction pending) {
        this.pending = pending;
    }
}
