package com.example.orderservice.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Qualifier("userServiceRestClient")
    public RestClient userServiceRestClient(RestClient.Builder restClientBuilder, UserServiceProperties properties) {
        return restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestInterceptor((request, body, execution) -> {
                    forwardAuthorization(request);
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    @Qualifier("productServiceRestClient")
    public RestClient productServiceRestClient(RestClient.Builder restClientBuilder, ProductServiceProperties properties) {
        return restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestInterceptor((request, body, execution) -> {
                    forwardAuthorization(request);
                    return execution.execute(request, body);
                })
                .build();
    }

    private void forwardAuthorization(org.springframework.http.HttpRequest request) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest incoming = attributes.getRequest();
            String authorization = incoming.getHeader(HttpHeaders.AUTHORIZATION);

            if (authorization != null) {
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
    }
}