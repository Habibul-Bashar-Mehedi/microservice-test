package com.example.event;

public record StockUpdateFailedEvent(Long orderId, String email) {
}
