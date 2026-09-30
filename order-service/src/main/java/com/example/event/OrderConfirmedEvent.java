package com.example.event;

public record OrderConfirmedEvent(Long orderId, Long productId, Integer quantity) {
}