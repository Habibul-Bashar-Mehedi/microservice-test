package com.example.logservice.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.logservice.controller.MessageLogController;
import com.example.logservice.entity.MessageLog;
import com.example.logservice.service.MessageLogSearchService;
import com.example.logservice.service.MessageLogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MessageLogController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@TestPropertySource(properties = "jwt.secret=7a4f0c9b2e5d8a1c3f6b9e2d4a7c0f3b6e9a2d4c7f0b3e6a9d2c4f7b0e3a6c")
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageLogService messageLogService;
    @MockitoBean
    private MessageLogSearchService messageLogSearchService;

    @Test
    void postLogs_isPermittedWithoutAuthentication() throws Exception {
        when(messageLogService.record(any(MessageLog.class))).thenReturn(MessageLog.builder().id(1L).build());

        mockMvc.perform(post("/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceName\":\"order-service\",\"direction\":\"PUBLISHED\","
                                + "\"routingKey\":\"order.created\",\"status\":\"SUCCESS\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void listLogs_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listLogs_allowsAdminRole() throws Exception {
        when(messageLogService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/v1/logs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void listLogs_forbidsNonAdminRole() throws Exception {
        mockMvc.perform(get("/v1/logs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }
}
