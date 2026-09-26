package com.example.orderservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "user-service")
public class UserServiceProperties {

    private String baseUrl;

    private HttpOperation getUser;
}