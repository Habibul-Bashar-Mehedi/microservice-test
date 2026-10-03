package com.example.authservice.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth2")
public record Oauth2Properties(String googleClientId, List<String> adminEmails) {
}