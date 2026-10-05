package com.example.orderservice.consumer;

import com.example.orderservice.client.LogClient;
import com.example.orderservice.config.KafkaConfig;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.event.OrderCreatedEvent;
import com.example.event.StockUpdateFailedEvent;
import com.example.event.StockUpdatedEvent;
import com.example.orderservice.repository.OrderRepository;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderRepository orderRepository;
    private final LogClient logClient;
    private final CacheManager cacheManager;

    @Bean
    public Consumer<OrderCreatedEvent> orderCreatedListener() {
        return event -> {
            try {
                if (orderRepository.findById(event.orderId()).isEmpty()) {
                    log.warn("OrderCreated event for unknown order {}", event.orderId());
                    logClient.recordConsumeFailed(
                            KafkaConfig.ORDER_CREATED_TOPIC,
                            KafkaConfig.ORDER_CREATED_TOPIC,
                            event,
                            "unknown order " + event.orderId(),
                            event.email()
                    );
                    return;
                }
                logClient.recordConsume(
                        KafkaConfig.ORDER_CREATED_TOPIC,
                        KafkaConfig.ORDER_CREATED_TOPIC,
                        event,
                        event.email()
                );
                evict("orders", "ordersWithUser");
            } catch (Exception e) {
                log.error("Unexpected error handling order.created for order {}: {}", event.orderId(), e.getMessage(), e);
                logClient.recordConsumeFailed(
                        KafkaConfig.ORDER_CREATED_TOPIC,
                        KafkaConfig.ORDER_CREATED_TOPIC,
                        event,
                        e.getMessage(),
                        event.email()
                );
                throw e;
            }
        };
    }

    @Bean
    public Consumer<StockUpdatedEvent> stockUpdatedListener() {
        return event -> {
            try {
                Order order = orderRepository.findById(event.orderId()).orElse(null);

                if (order == null) {
                    log.warn("StockUpdated event for unknown order {}", event.orderId());
                    logClient.recordConsumeFailed(
                            KafkaConfig.STOCK_UPDATED_TOPIC,
                            KafkaConfig.STOCK_UPDATED_TOPIC,
                            event,
                            "unknown order " + event.orderId(),
                            event.email()
                    );
                    return;
                }

                if (order.getStatus() == OrderStatus.CANCELLED) {
                    log.info("Order {} cancelled, ignoring StockUpdated event", event.orderId());
                    logClient.recordConsume(
                            KafkaConfig.STOCK_UPDATED_TOPIC,
                            KafkaConfig.STOCK_UPDATED_TOPIC,
                            event,
                            event.email()
                    );
                    return;
                }

                order.setStatus(OrderStatus.CONFIRMED);
                order.setProductUpdated(true);
                orderRepository.save(order);
                log.info("Order {} confirmed after stock update", event.orderId());
                logClient.recordConsume(
                        KafkaConfig.STOCK_UPDATED_TOPIC,
                        KafkaConfig.STOCK_UPDATED_TOPIC,
                        event,
                        event.email()
                );
                evict("orders", "orderById", "ordersWithUser");
            } catch (Exception e) {
                log.error("Unexpected error handling stock.updated for order {}: {}", event.orderId(), e.getMessage(), e);
                logClient.recordConsumeFailed(
                        KafkaConfig.STOCK_UPDATED_TOPIC,
                        KafkaConfig.STOCK_UPDATED_TOPIC,
                        event,
                        e.getMessage(),
                        event.email()
                );
                throw e;
            }
        };
    }

    @Bean
    public Consumer<StockUpdateFailedEvent> stockUpdateFailedListener() {
        return event -> {
            try {
                Order order = orderRepository.findById(event.orderId()).orElse(null);

                if (order == null) {
                    log.warn("StockUpdateFailed event for unknown order {}", event.orderId());
                    logClient.recordConsumeFailed(
                            KafkaConfig.STOCK_FAILED_TOPIC,
                            KafkaConfig.STOCK_FAILED_TOPIC,
                            event,
                            "unknown order " + event.orderId(),
                            event.email()
                    );
                    return;
                }

                if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.CANCELLED) {
                    logClient.recordConsume(
                            KafkaConfig.STOCK_FAILED_TOPIC,
                            KafkaConfig.STOCK_FAILED_TOPIC,
                            event,
                            event.email()
                    );
                    return;
                }

                order.setStatus(OrderStatus.REJECTED);
                order.setProductUpdated(false);
                orderRepository.save(order);
                log.info("Order {} rejected: stock update failed", event.orderId());
                logClient.recordConsume(
                        KafkaConfig.STOCK_FAILED_TOPIC,
                        KafkaConfig.STOCK_FAILED_TOPIC,
                        event,
                        event.email()
                );
                evict("orders", "orderById", "ordersWithUser");
            } catch (Exception e) {
                log.error("Unexpected error handling stock.failed for order {}: {}", event.orderId(), e.getMessage(), e);
                logClient.recordConsumeFailed(
                        KafkaConfig.STOCK_FAILED_TOPIC,
                        KafkaConfig.STOCK_FAILED_TOPIC,
                        event,
                        e.getMessage(),
                        event.email()
                );
                throw e;
            }
        };
    }

    private void evict(String... cacheNames) {
        for (String name : cacheNames) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}