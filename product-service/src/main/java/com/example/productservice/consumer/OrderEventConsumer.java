package com.example.productservice.consumer;

import com.example.productservice.config.RabbitConfig;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.StockUpdate;
import com.example.productservice.event.OrderConfirmedEvent;
import com.example.productservice.event.StockUpdateFailedEvent;
import com.example.productservice.event.StockUpdatedEvent;
import com.example.productservice.repository.StockUpdateRepository;
import com.example.productservice.service.InsufficientStockException;
import com.example.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final ProductService productService;
    private final RabbitTemplate rabbitTemplate;
    private final StockUpdateRepository stockUpdateRepository;

    @RabbitListener(queues = RabbitConfig.ORDER_CONFIRMED_QUEUE)
    @Transactional
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        if (event.productId() == null || event.quantity() == null || event.quantity() <= 0) {
            log.warn("Ignoring malformed order.confirmed event: {}", event);
            return;
        }

        if (stockUpdateRepository.existsById(event.orderId())) {
            log.info("Duplicate order.confirmed for order {}, stock already updated", event.orderId());
            return;
        }

        Product updated;
        try {
            updated = productService.updateQuantity(event.productId(), event.quantity());
        } catch (InsufficientStockException e) {
            log.warn("Stock update failed for order {}: {}", event.orderId(), e.getMessage());
            publishAfterCommit(
                    RabbitConfig.STOCK_FAILED_ROUTING_KEY,
                    new StockUpdateFailedEvent(event.orderId())
            );
            return;
        }

        if (updated == null) {
            log.error("Stock update failed: product {} not found for order {}", event.productId(), event.orderId());
            publishAfterCommit(
                    RabbitConfig.STOCK_FAILED_ROUTING_KEY,
                    new StockUpdateFailedEvent(event.orderId())
            );
            return;
        }

        stockUpdateRepository.save(new StockUpdate(event.orderId()));
        publishAfterCommit(
                RabbitConfig.STOCK_UPDATED_ROUTING_KEY,
                new StockUpdatedEvent(event.orderId())
        );
        log.info("Stock updated for product {} and acknowledged order {}", event.productId(), event.orderId());
    }

    private void publishAfterCommit(String routingKey, Object payload) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, payload);
            }
        });
    }
}