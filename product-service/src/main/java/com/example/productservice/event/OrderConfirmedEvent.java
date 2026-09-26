package com.example.productservice.event;

public record OrderConfirmedEvent(Long orderId, Long productId, Integer quantity) {
}