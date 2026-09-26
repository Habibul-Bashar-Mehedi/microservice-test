package com.example.orderservice.client;

import com.example.orderservice.config.HttpOperation;
import com.example.orderservice.config.ProductServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class RestClientProductClient implements ProductClient {

    private final @Qualifier("productServiceRestClient") RestClient productServiceRestClient;
    private final ProductServiceProperties properties;

    @Override
    public void updateQuantity(Long productId, Integer quantity) {
        HttpOperation operation = properties.getUpdateQuantity();

        productServiceRestClient.method(operation.getMethod())
                .uri(operation.getPath(), productId)
                .body(quantity)
                .retrieve()
                .toBodilessEntity();
    }
}