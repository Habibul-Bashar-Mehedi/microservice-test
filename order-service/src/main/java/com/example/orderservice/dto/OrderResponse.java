package com.example.orderservice.dto;

import com.example.orderservice.entity.Order;

public record OrderResponse(
        Long id,
        Long userId,
        Long productId,
        Integer quantity,
        String status,
        Boolean productUpdated,
        String userName,
        String userEmail) {

    public static OrderResponse from(Order order, String userName, String userEmail) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus().name(),
                order.getProductUpdated(),
                userName,
                userEmail);
    }
}