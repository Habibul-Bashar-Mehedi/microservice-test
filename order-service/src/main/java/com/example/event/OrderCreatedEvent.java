package com.example.event;

public record OrderCreatedEvent(Long orderId, Long userId, Long productId, Integer quantity, String email) {
}
