package com.example.orderservice.client;

import com.example.orderservice.config.HttpOperation;
import com.example.orderservice.config.ProductServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class RestClientProductClient implements ProductClient {

    private final @Qualifier("productServiceRestClient") RestClient productServiceRestClient;
    private final ProductServiceProperties properties;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    @Override
    public void updateQuantity(Long productId, Integer quantity) {
        circuitBreakerFactory.create("productService").run(
                () -> {
                    HttpOperation operation = properties.getUpdateQuantity();
                    productServiceRestClient.method(operation.getMethod())
                            .uri(operation.getPath(), productId)
                            .body(quantity)
                            .retrieve()
                            .toBodilessEntity();
                    return null;
                },
                throwable -> {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "product-service is unavailable: " + throwable.getMessage(),
                            throwable
                    );
                });
    }
}