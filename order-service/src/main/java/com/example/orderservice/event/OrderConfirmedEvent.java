package com.example.orderservice.event;

public record OrderConfirmedEvent(Long orderId, Long productId, Integer quantity) {
}