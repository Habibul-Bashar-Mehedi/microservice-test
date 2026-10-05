package com.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.event.OrderConfirmedEvent;
import com.example.event.StockUpdateFailedEvent;
import com.example.event.StockUpdatedEvent;
import com.example.productservice.client.LogClient;
import com.example.productservice.config.KafkaConfig;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.StockUpdate;
import com.example.productservice.repository.StockUpdateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class OrderStockProcessorTest {

    @Mock
    private ProductService productService;
    @Mock
    private StreamBridge streamBridge;
    @Mock
    private StockUpdateRepository stockUpdateRepository;
    @Mock
    private LogClient logClient;

    private OrderStockProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OrderStockProcessor(productService, streamBridge, stockUpdateRepository, logClient);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private OrderConfirmedEvent event(Long productId, Integer quantity) {
        return new OrderConfirmedEvent(5L, productId, quantity, "alice@example.com");
    }

    private void afterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    void process_ignoresMalformedEventWithNullProductId() {
        processor.process(event(null, 2));

        verify(productService, never()).updateQuantity(anyLong(), any());
        verify(stockUpdateRepository, never()).existsById(anyLong());
        verify(logClient).recordConsumeFailed(eq(KafkaConfig.ORDER_CONFIRMED_TOPIC),
                eq(KafkaConfig.ORDER_CONFIRMED_TOPIC), any(), any(), eq("alice@example.com"));
    }

    @Test
    void process_ignoresMalformedEventWithNonPositiveQuantity() {
        processor.process(event(10L, 0));

        verify(productService, never()).updateQuantity(anyLong(), any());
        verify(logClient).recordConsumeFailed(eq(KafkaConfig.ORDER_CONFIRMED_TOPIC),
                eq(KafkaConfig.ORDER_CONFIRMED_TOPIC), any(), any(), eq("alice@example.com"));
    }

    @Test
    void process_ignoresMalformedEventWithNullQuantity() {
        processor.process(event(10L, null));

        verify(productService, never()).updateQuantity(anyLong(), any());
        verify(stockUpdateRepository, never()).existsById(anyLong());
        verify(logClient).recordConsumeFailed(eq(KafkaConfig.ORDER_CONFIRMED_TOPIC),
                eq(KafkaConfig.ORDER_CONFIRMED_TOPIC), any(), any(), eq("alice@example.com"));
    }

    @Test
    void process_skipsAlreadyProcessedOrder() {
        when(stockUpdateRepository.existsById(5L)).thenReturn(true);

        processor.process(event(10L, 2));

        verify(productService, never()).updateQuantity(anyLong(), any());
        verify(logClient).recordConsume(eq(KafkaConfig.ORDER_CONFIRMED_TOPIC),
                eq(KafkaConfig.ORDER_CONFIRMED_TOPIC), any(), eq("alice@example.com"));
        verify(stockUpdateRepository, never()).save(any());
    }

    @Test
    void process_publishesStockFailedWhenStockIsInsufficient() {
        when(stockUpdateRepository.existsById(5L)).thenReturn(false);
        when(productService.updateQuantity(10L, 2)).thenThrow(new InsufficientStockException("not enough"));
        when(streamBridge.send(eq("stockFailed-out-0"), any())).thenReturn(true);

        processor.process(event(10L, 2));
        afterCommit();

        verify(logClient).recordConsume(eq(KafkaConfig.ORDER_CONFIRMED_TOPIC),
                eq(KafkaConfig.ORDER_CONFIRMED_TOPIC), any(), eq("alice@example.com"));
        verify(logClient).recordConsumeFailed(eq(KafkaConfig.ORDER_CONFIRMED_TOPIC),
                eq(KafkaConfig.ORDER_CONFIRMED_TOPIC), any(), eq("not enough"), eq("alice@example.com"));
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(streamBridge).send(eq("stockFailed-out-0"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(StockUpdateFailedEvent.class);
        verify(logClient).recordPublish(eq(KafkaConfig.STOCK_FAILED_TOPIC), any(), eq("alice@example.com"));
        verify(stockUpdateRepository, never()).save(any());
    }

    @Test
    void process_publishesStockFailedWhenProductIsMissing() {
        when(stockUpdateRepository.existsById(5L)).thenReturn(false);
        when(productService.updateQuantity(10L, 2)).thenReturn(null);
        when(streamBridge.send(eq("stockFailed-out-0"), any())).thenReturn(true);

        processor.process(event(10L, 2));
        afterCommit();

        verify(logClient).recordConsumeFailed(eq(KafkaConfig.ORDER_CONFIRMED_TOPIC),
                eq(KafkaConfig.ORDER_CONFIRMED_TOPIC), any(), any(), eq("alice@example.com"));
        verify(streamBridge).send(eq("stockFailed-out-0"), any());
        verify(stockUpdateRepository, never()).save(any());
    }

    @Test
    void process_updatesStockAndPublishesSuccessAfterCommit() {
        when(stockUpdateRepository.existsById(5L)).thenReturn(false);
        when(productService.updateQuantity(10L, 2))
                .thenReturn(Product.builder().id(10L).availableQuantity(3).build());
        when(streamBridge.send(eq("stockUpdated-out-0"), any())).thenReturn(true);

        processor.process(event(10L, 2));
        afterCommit();

        ArgumentCaptor<StockUpdate> saved = ArgumentCaptor.forClass(StockUpdate.class);
        verify(stockUpdateRepository).save(saved.capture());
        assertThat(saved.getValue().getOrderId()).isEqualTo(5L);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(streamBridge).send(eq("stockUpdated-out-0"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(StockUpdatedEvent.class);
        verify(logClient).recordPublish(eq(KafkaConfig.STOCK_UPDATED_TOPIC), any(), eq("alice@example.com"));
    }

    @Test
    void process_recordsPublishFailureWhenStreamBridgeRejectsAfterCommit() {
        when(stockUpdateRepository.existsById(5L)).thenReturn(false);
        when(productService.updateQuantity(10L, 2))
                .thenReturn(Product.builder().id(10L).availableQuantity(3).build());
        when(streamBridge.send(eq("stockUpdated-out-0"), any())).thenReturn(false);

        processor.process(event(10L, 2));

        assertThatThrownBy(this::afterCommit).isInstanceOf(IllegalStateException.class);
        verify(logClient).recordPublishFailed(eq(KafkaConfig.STOCK_UPDATED_TOPIC), any(), anyString());
    }

    @Test
    void process_propagatesUnexpectedFailureAndRecordsConsumeFailure() {
        when(stockUpdateRepository.existsById(5L)).thenReturn(false);
        when(productService.updateQuantity(10L, 2)).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> processor.process(event(10L, 2)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        verify(logClient).recordConsumeFailed(eq(KafkaConfig.ORDER_CONFIRMED_TOPIC),
                eq(KafkaConfig.ORDER_CONFIRMED_TOPIC), any(), eq("db down"), eq("alice@example.com"));
    }
}
