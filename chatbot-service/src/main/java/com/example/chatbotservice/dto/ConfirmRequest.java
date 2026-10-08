package com.example.chatbotservice.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRequest(
        @NotBlank(message = "conversationId is required") String conversationId,
        @NotBlank(message = "confirmationId is required") String confirmationId) {
}
