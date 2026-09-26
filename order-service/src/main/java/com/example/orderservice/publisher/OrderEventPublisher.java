package com.example.orderservice.publisher;

import com.example.orderservice.entity.Order;

public interface OrderEventPublisher {

    void publishCreated(Order order);

    void publishConfirmed(Order order);
}