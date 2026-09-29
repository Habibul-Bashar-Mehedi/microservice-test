package com.example.orderservice.publisher;

import com.example.orderservice.client.LogClient;
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
    private final LogClient logClient;

    @Override
    public void publishCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(order.getId(), order.getUserId(), order.getProductId(), order.getQuantity());
        publish(RabbitConfig.ORDER_CREATED_ROUTING_KEY, event);
    }

    @Override
    public void publishConfirmed(Order order) {
        OrderConfirmedEvent event = new OrderConfirmedEvent(order.getId(), order.getProductId(), order.getQuantity());
        publish(RabbitConfig.ORDER_CONFIRMED_ROUTING_KEY, event);
    }

    private void publish(String routingKey, Object event) {
        logClient.recordPublish(routingKey, event);
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, event);
        } catch (Exception e) {
            logClient.recordPublishFailed(routingKey, event, e.getMessage());
            throw e;
        }
    }
}