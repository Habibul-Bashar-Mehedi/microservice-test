package com.example.orderservice.client;

public interface ProductClient {

    void updateQuantity(Long productId, Integer quantity);
}