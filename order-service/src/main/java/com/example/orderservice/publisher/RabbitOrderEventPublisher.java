package com.example.orderservice.publisher;

import com.example.orderservice.config.RabbitConfig;
import com.example.orderservice.entity.Order;
import com.example.orderservice.event.OrderConfirmedEvent;
import com.example.orderservice.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitOrderEventPublisher implements OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishCreated(Order order) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ORDER_CREATED_ROUTING_KEY,
                new OrderCreatedEvent(order.getId(), order.getUserId(), order.getProductId(), order.getQuantity())
        );
    }

    @Override
    public void publishConfirmed(Order order) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ORDER_CONFIRMED_ROUTING_KEY,
                new OrderConfirmedEvent(order.getId(), order.getProductId(), order.getQuantity())
        );
    }
}