package com.example.orderservice.publisher;

import com.example.orderservice.client.LogClient;
import com.example.orderservice.config.KafkaConfig;
import com.example.orderservice.entity.Order;
import com.example.orderservice.event.OrderConfirmedEvent;
import com.example.orderservice.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final LogClient logClient;

    @Override
    public void publishCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(order.getId(), order.getUserId(), order.getProductId(), order.getQuantity());
        publish(KafkaConfig.ORDER_CREATED_TOPIC, event);
    }

    @Override
    public void publishConfirmed(Order order) {
        OrderConfirmedEvent event = new OrderConfirmedEvent(order.getId(), order.getProductId(), order.getQuantity());
        publish(KafkaConfig.ORDER_CONFIRMED_TOPIC, event);
    }

    private void publish(String topic, Object event) {
        logClient.recordPublish(topic, event);
        try {
            kafkaTemplate.send(topic, event);
        } catch (Exception e) {
            logClient.recordPublishFailed(topic, event, e.getMessage());
            throw e;
        }
    }
}