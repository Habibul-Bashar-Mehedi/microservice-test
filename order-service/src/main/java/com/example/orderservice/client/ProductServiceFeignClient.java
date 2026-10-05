package com.example.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service", contextId = "v3ProductService", fallback = ProductServiceFeignClientFallback.class)
public interface ProductServiceFeignClient {

    @PutMapping("/v1/products/{id}/quantity")
    void updateQuantity(@PathVariable("id") Long id, @RequestBody Integer quantity);
}