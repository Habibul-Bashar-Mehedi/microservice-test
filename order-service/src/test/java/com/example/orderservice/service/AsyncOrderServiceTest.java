package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.publisher.OrderEventPublisher;
import com.example.orderservice.repository.OrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AsyncOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventPublisher eventPublisher;

    private AsyncOrderService asyncOrderService;

    @BeforeEach
    void setUp() {
        asyncOrderService = new AsyncOrderService(orderRepository, eventPublisher);
    }

    private Order order(Long id, OrderStatus status) {
        return Order.builder().id(id).userId(1L).productId(10L).quantity(2).status(status).build();
    }

    @Test
    void create_rejectsNonPositiveQuantity() {
        Order order = Order.builder().userId(1L).productId(10L).quantity(-1).build();

        assertThatThrownBy(() -> asyncOrderService.create(order))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_publishesCreatedEvent() {
        when(orderRepository.findFirstByUserIdAndProductIdAndQuantityAndStatusIn(anyLong(), anyLong(), any(), anyList()))
                .thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = asyncOrderService.create(
                Order.builder().userId(1L).productId(10L).quantity(2).build());

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(eventPublisher).publishCreated(saved);
    }

    @Test
    void create_returnsDuplicateWithoutPublishing() {
        Order existing = order(9L, OrderStatus.PENDING);
        when(orderRepository.findFirstByUserIdAndProductIdAndQuantityAndStatusIn(anyLong(), anyLong(), any(), anyList()))
                .thenReturn(Optional.of(existing));

        Order result = asyncOrderService.create(
                Order.builder().userId(1L).productId(10L).quantity(2).build());

        assertThat(result).isSameAs(existing);
        verify(eventPublisher, never()).publishCreated(any());
    }

    @Test
    void confirm_pendingOrderMovesToConfirmingAndPublishes() {
        Order order = order(1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = asyncOrderService.confirm(1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMING);
        verify(eventPublisher).publishConfirmed(order);
    }

    @Test
    void confirm_alreadyFinalOrderDoesNothing() {
        Order order = order(1L, OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        asyncOrderService.confirm(1L);

        verify(eventPublisher, never()).publishConfirmed(any());
    }

    @Test
    void confirm_missingOrderReturnsNull() {
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());
        assertThat(asyncOrderService.confirm(5L)).isNull();
    }

    @Test
    void findById_delegates() {
        Order order = order(1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThat(asyncOrderService.findById(1L)).isSameAs(order);
    }
}