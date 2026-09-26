package com.example.orderservice.consumer;

import com.example.orderservice.client.UserClient;
import com.example.orderservice.config.RabbitConfig;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.event.StockUpdateFailedEvent;
import com.example.orderservice.event.StockUpdatedEvent;
import com.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderRepository orderRepository;
    private final UserClient userClient;

    @RabbitListener(queues = RabbitConfig.ORDER_CREATED_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);

        if (order == null) {
            log.warn("OrderCreated event for unknown order {}", event.orderId());
            return;
        }

        if (!userClient.isActive(event.userId())) {
            order.setStatus(OrderStatus.REJECTED);
            orderRepository.save(order);
            log.warn("Order {} rejected: user {} is not active", event.orderId(), event.userId());
        }
    }

    @RabbitListener(queues = RabbitConfig.STOCK_UPDATED_QUEUE)
    public void onStockUpdated(StockUpdatedEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);

        if (order == null) {
            log.warn("StockUpdated event for unknown order {}", event.orderId());
            return;
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order.setProductUpdated(true);
        orderRepository.save(order);
        log.info("Order {} confirmed after stock update", event.orderId());
    }

    @RabbitListener(queues = RabbitConfig.STOCK_FAILED_QUEUE)
    public void onStockUpdateFailed(StockUpdateFailedEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);

        if (order == null) {
            log.warn("StockUpdateFailed event for unknown order {}", event.orderId());
            return;
        }

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return;
        }

        order.setStatus(OrderStatus.REJECTED);
        order.setProductUpdated(false);
        orderRepository.save(order);
        log.info("Order {} rejected: stock update failed", event.orderId());
    }
}