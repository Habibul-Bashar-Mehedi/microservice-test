package com.example.chatbotservice.dto;

public record ChatResponse(
        String conversationId,
        String reply,
        boolean requiresConfirmation,
        String confirmationId,
        String confirmationSummary) {

    public static ChatResponse reply(String conversationId, String reply) {
        return new ChatResponse(conversationId, reply, false, null, null);
    }

    public static ChatResponse confirmation(String conversationId, String reply,
            String confirmationId, String summary) {
        return new ChatResponse(conversationId, reply, true, confirmationId, summary);
    }
}
