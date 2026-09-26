package com.example.orderservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "product-service")
public class ProductServiceProperties {

    private String baseUrl;

    private HttpOperation updateQuantity;
}