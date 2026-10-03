package com.example.orderservice.publisher;

import com.example.orderservice.client.LogClient;
import com.example.orderservice.client.UserClient;
import com.example.orderservice.config.KafkaConfig;
import com.example.orderservice.entity.Order;
import com.example.event.OrderConfirmedEvent;
import com.example.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    static final String ORDER_CREATED_BINDING = "orderCreated-out-0";
    static final String ORDER_CONFIRMED_BINDING = "orderConfirmed-out-0";

    private final StreamBridge streamBridge;
    private final LogClient logClient;
    private final UserClient userClient;

    @Override
    public void publishCreated(Order order) {
        String email = userClient.getEmail(order.getUserId());
        OrderCreatedEvent event = new OrderCreatedEvent(order.getId(), order.getUserId(), order.getProductId(), order.getQuantity(), email);
        publish(ORDER_CREATED_BINDING, KafkaConfig.ORDER_CREATED_TOPIC, event, email);
    }

    @Override
    public void publishConfirmed(Order order) {
        String email = userClient.getEmail(order.getUserId());
        OrderConfirmedEvent event = new OrderConfirmedEvent(order.getId(), order.getProductId(), order.getQuantity(), email);
        publish(ORDER_CONFIRMED_BINDING, KafkaConfig.ORDER_CONFIRMED_TOPIC, event, email);
    }

    private void publish(String binding, String topic, Object event, String email) {
        logClient.recordPublish(topic, event, email);
        try {
            boolean sent = streamBridge.send(binding, event);
            if (!sent) {
                throw new IllegalStateException("StreamBridge failed to send to " + topic);
            }
        } catch (Exception e) {
            logClient.recordPublishFailed(topic, event, e.getMessage());
            throw e;
        }
    }
}