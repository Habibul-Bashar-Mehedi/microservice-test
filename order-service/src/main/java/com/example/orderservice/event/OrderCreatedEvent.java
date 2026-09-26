package com.example.orderservice.event;

public record OrderCreatedEvent(Long orderId, Long userId, Long productId, Integer quantity) {
}