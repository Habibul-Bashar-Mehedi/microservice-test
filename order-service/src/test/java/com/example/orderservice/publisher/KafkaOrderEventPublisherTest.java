package com.example.orderservice.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.event.OrderConfirmedEvent;
import com.example.event.OrderCreatedEvent;
import com.example.orderservice.client.LogClient;
import com.example.orderservice.client.UserClient;
import com.example.orderservice.config.KafkaConfig;
import com.example.orderservice.entity.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;

@ExtendWith(MockitoExtension.class)
class KafkaOrderEventPublisherTest {

    @Mock
    private StreamBridge streamBridge;
    @Mock
    private LogClient logClient;
    @Mock
    private UserClient userClient;

    private KafkaOrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaOrderEventPublisher(streamBridge, logClient, userClient);
    }

    private Order order() {
        return Order.builder().id(5L).userId(1L).productId(10L).quantity(2).build();
    }

    @Test
    void publishCreated_sendsOrderCreatedEventAndRecordsPublish() {
        when(userClient.getEmail(1L)).thenReturn("alice@example.com");
        when(streamBridge.send(eq(KafkaOrderEventPublisher.ORDER_CREATED_BINDING), any())).thenReturn(true);

        publisher.publishCreated(order());

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(streamBridge).send(eq("orderCreated-out-0"), event.capture());
        assertThat(event.getValue()).isInstanceOf(OrderCreatedEvent.class);
        OrderCreatedEvent payload = (OrderCreatedEvent) event.getValue();
        assertThat(payload.orderId()).isEqualTo(5L);
        assertThat(payload.userId()).isEqualTo(1L);
        assertThat(payload.productId()).isEqualTo(10L);
        assertThat(payload.quantity()).isEqualTo(2);
        assertThat(payload.email()).isEqualTo("alice@example.com");
        verify(logClient).recordPublish(eq(KafkaConfig.ORDER_CREATED_TOPIC), any(), eq("alice@example.com"));
    }

    @Test
    void publishConfirmed_sendsOrderConfirmedEventAndRecordsPublish() {
        when(userClient.getEmail(1L)).thenReturn("alice@example.com");
        when(streamBridge.send(eq(KafkaOrderEventPublisher.ORDER_CONFIRMED_BINDING), any())).thenReturn(true);

        publisher.publishConfirmed(order());

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(streamBridge).send(eq("orderConfirmed-out-0"), event.capture());
        assertThat(event.getValue()).isInstanceOf(OrderConfirmedEvent.class);
        OrderConfirmedEvent payload = (OrderConfirmedEvent) event.getValue();
        assertThat(payload.orderId()).isEqualTo(5L);
        assertThat(payload.productId()).isEqualTo(10L);
        assertThat(payload.quantity()).isEqualTo(2);
        assertThat(payload.email()).isEqualTo("alice@example.com");
        verify(logClient).recordPublish(eq(KafkaConfig.ORDER_CONFIRMED_TOPIC), any(), eq("alice@example.com"));
    }

    @Test
    void publishCreated_throwsAndRecordsFailureWhenStreamBridgeRejects() {
        when(userClient.getEmail(1L)).thenReturn("alice@example.com");
        when(streamBridge.send(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> publisher.publishCreated(order()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(KafkaConfig.ORDER_CREATED_TOPIC);

        verify(logClient).recordPublishFailed(eq(KafkaConfig.ORDER_CREATED_TOPIC), any(), anyString());
    }

    @Test
    void publishCreated_throwsAndRecordsFailureWhenStreamBridgeThrows() {
        when(userClient.getEmail(1L)).thenReturn("alice@example.com");
        when(streamBridge.send(anyString(), any())).thenThrow(new RuntimeException("broker down"));

        assertThatThrownBy(() -> publisher.publishCreated(order()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("broker down");

        verify(logClient).recordPublishFailed(eq(KafkaConfig.ORDER_CREATED_TOPIC), any(), eq("broker down"));
    }
}
