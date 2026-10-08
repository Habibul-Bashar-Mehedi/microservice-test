package com.example.chatbotservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chatbotservice.dto.ChatRequest;
import com.example.chatbotservice.dto.ChatResponse;
import com.example.chatbotservice.dto.ConfirmRequest;
import com.example.chatbotservice.service.ChatService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatService);
    }

    private Authentication auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void chatV1_usesEmailAndRole() {
        when(chatService.chat(any(), any(), any(), any())).thenReturn(ChatResponse.reply("c", "hi"));

        ChatResponse response = controller.chatV1(
                new ChatRequest("hello", null), auth("m@x.com", "MANAGER"), "Bearer t");

        assertThat(response.reply()).isEqualTo("hi");
        verify(chatService).chat("m@x.com", "MANAGER", "Bearer t", new ChatRequest("hello", null));
    }

    @Test
    void confirmV1_delegates() {
        when(chatService.confirm(any(), any(), any(), any())).thenReturn(ChatResponse.reply("c", "done"));

        controller.confirmV1(new ConfirmRequest("c", "id"), auth("m@x.com", "MANAGER"), "Bearer t");

        verify(chatService).confirm("m@x.com", "MANAGER", "Bearer t", new ConfirmRequest("c", "id"));
    }

    @Test
    void cancelV1_delegates() {
        when(chatService.cancel(any(), any())).thenReturn(ChatResponse.reply("c", "cancelled"));

        controller.cancelV1(new ConfirmRequest("c", "id"), auth("m@x.com", "MANAGER"));

        verify(chatService).cancel("m@x.com", "c");
    }

    @Test
    void currentRole_defaultsToUserWhenNoAuthorities() {
        Authentication anonymous = new UsernamePasswordAuthenticationToken("x", null, List.of());
        when(chatService.chat(any(), any(), any(), any())).thenReturn(ChatResponse.reply("c", "hi"));

        controller.chatV1(new ChatRequest("hi", null), anonymous, "Bearer t");

        verify(chatService).chat("x", "USER", "Bearer t", new ChatRequest("hi", null));
    }
}
