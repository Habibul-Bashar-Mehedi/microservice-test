package com.example.event;

public record StockUpdatedEvent(Long orderId, String email) {
}
