package com.example.productservice.service;

import com.example.productservice.client.LogClient;
import com.example.productservice.config.KafkaConfig;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.StockUpdate;
import com.example.event.OrderConfirmedEvent;
import com.example.event.StockUpdateFailedEvent;
import com.example.event.StockUpdatedEvent;
import com.example.productservice.repository.StockUpdateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStockProcessor {

    static final String STOCK_UPDATED_BINDING = "stockUpdated-out-0";
    static final String STOCK_FAILED_BINDING = "stockFailed-out-0";

    private final ProductService productService;
    private final StreamBridge streamBridge;
    private final StockUpdateRepository stockUpdateRepository;
    private final LogClient logClient;

    @Transactional
    public void process(OrderConfirmedEvent event) {
        try {
            if (event.productId() == null || event.quantity() == null || event.quantity() <= 0) {
                log.warn("Ignoring malformed order.confirmed event: {}", event);
                logClient.recordConsumeFailed(
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        event,
                        "malformed event: productId=" + event.productId() + ", quantity=" + event.quantity(),
                        event.email()
                );
                return;
            }

            if (stockUpdateRepository.existsById(event.orderId())) {
                log.info("Duplicate order.confirmed for order {}, stock already updated", event.orderId());
                logClient.recordConsume(
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        KafkaConfig.ORDER_CONFIRMED_TOPIC,
                        event,
                        event.email()
                );
                return;
            }

            logClient.recordConsume(
                    KafkaConfig.ORDER_CONFIRMED_TOPIC,
                    KafkaConfig.ORDER_CONFIRMED_TOPIC,
                    event,
                    event.email()
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
                        e.getMessage(),
                        event.email()
                );
                publishAfterCommit(
                        STOCK_FAILED_BINDING,
                        KafkaConfig.STOCK_FAILED_TOPIC,
                        new StockUpdateFailedEvent(event.orderId(), event.email()),
                        event.email()
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
                        detail,
                        event.email()
                );
                publishAfterCommit(
                        STOCK_FAILED_BINDING,
                        KafkaConfig.STOCK_FAILED_TOPIC,
                        new StockUpdateFailedEvent(event.orderId(), event.email()),
                        event.email()
                );
                return;
            }

            stockUpdateRepository.save(new StockUpdate(event.orderId()));
            publishAfterCommit(
                    STOCK_UPDATED_BINDING,
                    KafkaConfig.STOCK_UPDATED_TOPIC,
                    new StockUpdatedEvent(event.orderId(), event.email()),
                    event.email()
            );
            log.info("Stock updated for product {} and acknowledged order {}", event.productId(), event.orderId());
        } catch (Exception e) {
            log.error("Unexpected error handling order.confirmed for order {}: {}", event.orderId(), e.getMessage(), e);
            logClient.recordConsumeFailed(
                    KafkaConfig.ORDER_CONFIRMED_TOPIC,
                    KafkaConfig.ORDER_CONFIRMED_TOPIC,
                    event,
                    e.getMessage(),
                    event.email()
            );
            throw e;
        }
    }

    private void publishAfterCommit(String binding, String topic, Object payload, String email) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                logClient.recordPublish(topic, payload, email);
                try {
                    boolean sent = streamBridge.send(binding, payload);
                    if (!sent) {
                        throw new IllegalStateException("StreamBridge failed to send to " + topic);
                    }
                } catch (Exception e) {
                    logClient.recordPublishFailed(topic, payload, e.getMessage());
                    throw e;
                }
            }
        });
    }
}