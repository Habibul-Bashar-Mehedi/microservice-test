package com.example.orderservice.consumer;

import com.example.orderservice.client.LogClient;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderRepository orderRepository;
    private final LogClient logClient;

    @RabbitListener(queues = RabbitConfig.ORDER_CREATED_QUEUE)
    @CacheEvict(value = {"orders"}, allEntries = true)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            if (orderRepository.findById(event.orderId()).isEmpty()) {
                log.warn("OrderCreated event for unknown order {}", event.orderId());
                logClient.recordConsumeFailed(
                        RabbitConfig.ORDER_CREATED_ROUTING_KEY,
                        RabbitConfig.ORDER_CREATED_QUEUE,
                        event,
                        "unknown order " + event.orderId()
                );
                return;
            }
            logClient.recordConsume(
                    RabbitConfig.ORDER_CREATED_ROUTING_KEY,
                    RabbitConfig.ORDER_CREATED_QUEUE,
                    event
            );
        } catch (Exception e) {
            log.error("Unexpected error handling order.created for order {}: {}", event.orderId(), e.getMessage(), e);
            logClient.recordConsumeFailed(
                    RabbitConfig.ORDER_CREATED_ROUTING_KEY,
                    RabbitConfig.ORDER_CREATED_QUEUE,
                    event,
                    e.getMessage()
            );
            throw e;
        }
    }

    @RabbitListener(queues = RabbitConfig.STOCK_UPDATED_QUEUE)
    @CacheEvict(value = {"orders", "orderById"}, allEntries = true)
    public void onStockUpdated(StockUpdatedEvent event) {
        try {
            Order order = orderRepository.findById(event.orderId()).orElse(null);

            if (order == null) {
                log.warn("StockUpdated event for unknown order {}", event.orderId());
                logClient.recordConsumeFailed(
                        RabbitConfig.STOCK_UPDATED_ROUTING_KEY,
                        RabbitConfig.STOCK_UPDATED_QUEUE,
                        event,
                        "unknown order " + event.orderId()
                );
                return;
            }

            if (order.getStatus() == OrderStatus.CANCELLED) {
                log.info("Order {} cancelled, ignoring StockUpdated event", event.orderId());
                logClient.recordConsume(
                        RabbitConfig.STOCK_UPDATED_ROUTING_KEY,
                        RabbitConfig.STOCK_UPDATED_QUEUE,
                        event
                );
                return;
            }

            order.setStatus(OrderStatus.CONFIRMED);
            order.setProductUpdated(true);
            orderRepository.save(order);
            log.info("Order {} confirmed after stock update", event.orderId());
            logClient.recordConsume(
                    RabbitConfig.STOCK_UPDATED_ROUTING_KEY,
                    RabbitConfig.STOCK_UPDATED_QUEUE,
                    event
            );
        } catch (Exception e) {
            log.error("Unexpected error handling stock.updated for order {}: {}", event.orderId(), e.getMessage(), e);
            logClient.recordConsumeFailed(
                    RabbitConfig.STOCK_UPDATED_ROUTING_KEY,
                    RabbitConfig.STOCK_UPDATED_QUEUE,
                    event,
                    e.getMessage()
            );
            throw e;
        }
    }

    @RabbitListener(queues = RabbitConfig.STOCK_FAILED_QUEUE)
    @CacheEvict(value = {"orders", "orderById"}, allEntries = true)
    public void onStockUpdateFailed(StockUpdateFailedEvent event) {
        try {
            Order order = orderRepository.findById(event.orderId()).orElse(null);

            if (order == null) {
                log.warn("StockUpdateFailed event for unknown order {}", event.orderId());
                logClient.recordConsumeFailed(
                        RabbitConfig.STOCK_FAILED_ROUTING_KEY,
                        RabbitConfig.STOCK_FAILED_QUEUE,
                        event,
                        "unknown order " + event.orderId()
                );
                return;
            }

            if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.CANCELLED) {
                logClient.recordConsume(
                        RabbitConfig.STOCK_FAILED_ROUTING_KEY,
                        RabbitConfig.STOCK_FAILED_QUEUE,
                        event
                );
                return;
            }

            order.setStatus(OrderStatus.REJECTED);
            order.setProductUpdated(false);
            orderRepository.save(order);
            log.info("Order {} rejected: stock update failed", event.orderId());
            logClient.recordConsume(
                    RabbitConfig.STOCK_FAILED_ROUTING_KEY,
                    RabbitConfig.STOCK_FAILED_QUEUE,
                    event
            );
        } catch (Exception e) {
            log.error("Unexpected error handling stock.failed for order {}: {}", event.orderId(), e.getMessage(), e);
            logClient.recordConsumeFailed(
                    RabbitConfig.STOCK_FAILED_ROUTING_KEY,
                    RabbitConfig.STOCK_FAILED_QUEUE,
                    event,
                    e.getMessage()
            );
            throw e;
        }
    }
}