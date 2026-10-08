package com.example.chatbotservice.controller;

import com.example.chatbotservice.dto.ChatRequest;
import com.example.chatbotservice.dto.ChatResponse;
import com.example.chatbotservice.dto.ConfirmRequest;
import com.example.chatbotservice.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/v1/chat")
    public ChatResponse chatV1(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        return chatService.chat(authentication.getName(), currentRole(authentication), authHeader, request);
    }

    @PostMapping("/v1/chat/confirm")
    public ChatResponse confirmV1(
            @Valid @RequestBody ConfirmRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        return chatService.confirm(authentication.getName(), currentRole(authentication), authHeader, request);
    }

    @PostMapping("/v1/chat/cancel")
    public ChatResponse cancelV1(
            @Valid @RequestBody ConfirmRequest request,
            Authentication authentication) {
        return chatService.cancel(authentication.getName(), request.conversationId());
    }

    private String currentRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("USER");
    }
}
