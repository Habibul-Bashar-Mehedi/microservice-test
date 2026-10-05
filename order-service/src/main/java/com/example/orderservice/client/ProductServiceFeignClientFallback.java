package com.example.orderservice.client;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProductServiceFeignClientFallback implements ProductServiceFeignClient {

    @Override
    public void updateQuantity(Long id, Integer quantity) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "product-service is unavailable (circuit breaker open)"
        );
    }
}