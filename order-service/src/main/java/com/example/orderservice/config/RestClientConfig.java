package com.example.orderservice.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    @Qualifier("userServiceRestClient")
    public RestClient userServiceRestClient(UserServiceProperties properties) {
        return RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    @Bean
    @Qualifier("productServiceRestClient")
    public RestClient productServiceRestClient(ProductServiceProperties properties) {
        return RestClient.builder().baseUrl(properties.getBaseUrl()).build();
 }
}