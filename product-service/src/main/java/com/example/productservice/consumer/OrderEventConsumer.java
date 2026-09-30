package com.example.productservice.consumer;

import com.example.productservice.client.LogClient;
import com.example.productservice.config.KafkaConfig;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.StockUpdate;
import com.example.event.OrderConfirmedEvent;
import com.example.event.StockUpdateFailedEvent;
import com.example.event.StockUpdatedEvent;
import com.example.productservice.repository.StockUpdateRepository;
import com.example.productservice.service.InsufficientStockException;
import com.example.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final ProductService productService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StockUpdateRepository stockUpdateRepository;
    private final LogClient logClient;

    @KafkaListener(topics = KafkaConfig.ORDER_CONFIRMED_TOPIC, groupId = KafkaConfig.PRODUCT_SERVICE_GROUP)
    @Transactional
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        try {
            if (event.productId() == null || event.quantity() == null || event.quantity() <= 0) {
                log.warn("Ignoring malformed order.confirmed event: {}", event);
                logClient.recordConsumeFailed(
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        event,
                        "malformed event: productId=" + event.productId() + ", quantity=" + event.quantity()
                );
                return;
            }

            if (stockUpdateRepository.existsById(event.orderId())) {
                log.info("Duplicate order.confirmed for order {}, stock already updated", event.orderId());
                logClient.recordConsume(
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        event
                );
                return;
            }

            logClient.recordConsume(
                    KafkaConfig.ORDER_CONFIRMED_TOPIC,
                    KafkaConfig.ORDER_CONFIRMED_TOPIC,
                    event
            );

            Product updated;
            try {
                updated = productService.updateQuantity(event.productId(), event.quantity());
            } catch (InsufficientStockException e) {
                log.warn("Stock update failed for order {}: {}", event.orderId(), e.getMessage());
                logClient.recordConsumeFailed(
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        event,
                        e.getMessage()
                );
                publishAfterCommit(
                        KafkaConfig.STOCK_FAILED_TOPIC,
                        new StockUpdateFailedEvent(event.orderId())
                );
                return;
            }

            if (updated == null) {
                String detail = "product " + event.productId() + " not found for order " + event.orderId();
                log.error("Stock update failed: {}", detail);
                logClient.recordConsumeFailed(
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        event,
                        detail
                );
                publishAfterCommit(
                        KafkaConfig.STOCK_FAILED_TOPIC,
                        new StockUpdateFailedEvent(event.orderId())
                );
                return;
            }

            stockUpdateRepository.save(new StockUpdate(event.orderId()));
            publishAfterCommit(
                    KafkaConfig.STOCK_UPDATED_TOPIC,
                    new StockUpdatedEvent(event.orderId())
            );
            log.info("Stock updated for product {} and acknowledged order {}", event.productId(), event.orderId());
        } catch (Exception e) {
            log.error("Unexpected error handling order.confirmed for order {}: {}", event.orderId(), e.getMessage(), e);
            logClient.recordConsumeFailed(
                    KafkaConfig.ORDER_CONFIRMED_TOPIC,
                    KafkaConfig.ORDER_CONFIRMED_TOPIC,
                    event,
                    e.getMessage()
            );
            throw e;
        }
    }

    private void publishAfterCommit(String topic, Object payload) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                logClient.recordPublish(topic, payload);
                try {
                    kafkaTemplate.send(topic, payload);
                } catch (Exception e) {
                    logClient.recordPublishFailed(topic, payload, e.getMessage());
                    throw e;
                }
            }
        });
    }
}