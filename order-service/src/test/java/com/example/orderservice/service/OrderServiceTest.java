package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.orderservice.client.ProductClient;
import com.example.orderservice.client.ProductServiceFeignClient;
import com.example.orderservice.client.UserClient;
import com.example.orderservice.client.UserProfile;
import com.example.orderservice.client.UserServiceFeignClient;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserClient userClient;
    @Mock
    private ProductClient productClient;
    @Mock
    private UserServiceFeignClient userServiceFeignClient;
    @Mock
    private ProductServiceFeignClient productServiceFeignClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, userClient, productClient,
                userServiceFeignClient, productServiceFeignClient);
    }

    private Order order(Long id, Long userId, OrderStatus status) {
        return Order.builder().id(id).userId(userId).productId(10L).quantity(2).status(status).build();
    }

    private void stubSave() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void noDuplicate() {
        when(orderRepository.findFirstByUserIdAndProductIdAndQuantityAndStatusIn(anyLong(), anyLong(), any(), anyList()))
                .thenReturn(Optional.empty());
    }

    // ---- v1 create (RestClient client) ----

    @Test
    void create_rejectsNonPositiveQuantity() {
        Order order = Order.builder().userId(1L).productId(10L).quantity(0).build();

        assertThatThrownBy(() -> orderService.create(order))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsInactiveUser() {
        when(userClient.isActive(1L)).thenReturn(false);
        Order order = Order.builder().userId(1L).productId(10L).quantity(2).build();

        assertThatThrownBy(() -> orderService.create(order))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void create_persistsPendingOrderForActiveUser() {
        when(userClient.isActive(1L)).thenReturn(true);
        noDuplicate();
        stubSave();

        Order result = orderService.create(Order.builder().userId(1L).productId(10L).quantity(2).build());

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getProductUpdated()).isFalse();
    }

    @Test
    void create_returnsExistingDuplicate() {
        when(userClient.isActive(1L)).thenReturn(true);
        Order existing = order(99L, 1L, OrderStatus.PENDING);
        when(orderRepository.findFirstByUserIdAndProductIdAndQuantityAndStatusIn(anyLong(), anyLong(), any(), anyList()))
                .thenReturn(Optional.of(existing));

        Order result = orderService.create(Order.builder().userId(1L).productId(10L).quantity(2).build());

        assertThat(result).isSameAs(existing);
        verify(orderRepository, never()).save(any());
    }

    // ---- v3 create (Feign client) ----

    @Test
    void createV3_rejectsInactiveUserViaFeign() {
        when(userServiceFeignClient.getUser(1L)).thenReturn(new UserProfile(1L, "A", "a@x.com", false));

        assertThatThrownBy(() -> orderService.createV3(
                Order.builder().userId(1L).productId(10L).quantity(2).build()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createV3_persistsPendingOrderForActiveUser() {
        when(userServiceFeignClient.getUser(1L)).thenReturn(new UserProfile(1L, "A", "a@x.com", true));
        noDuplicate();
        stubSave();

        Order result = orderService.createV3(Order.builder().userId(1L).productId(10L).quantity(2).build());

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // ---- confirm ----

    @Test
    void confirm_returnsNullWhenOrderMissing() {
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());
        assertThat(orderService.confirm(5L)).isNull();
    }

    @Test
    void confirm_updatesStockAndMarksConfirmed() {
        Order order = order(1L, 1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        stubSave();

        Order result = orderService.confirm(1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getProductUpdated()).isTrue();
        verify(productClient).updateQuantity(10L, 2);
    }

    @Test
    void confirm_doesNothingForAlreadyFinalOrder() {
        Order order = order(1L, 1L, OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Order result = orderService.confirm(1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productClient, never()).updateQuantity(anyLong(), any());
    }

    @Test
    void confirmV3_updatesStockViaFeign() {
        Order order = order(1L, 1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        stubSave();

        Order result = orderService.confirmV3(1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(productServiceFeignClient).updateQuantity(10L, 2);
    }

    // ---- cancel ----

    @Test
    void cancel_onlyOwnOrders() {
        Order order = order(1L, 1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(1L, 2L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void cancel_rejectsAlreadyFinalOrder() {
        Order order = order(1L, 1L, OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(1L, 1L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancel_marksCancelled() {
        Order order = order(1L, 1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        stubSave();

        Order result = orderService.cancel(1L, 1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_returnsNullWhenMissing() {
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());
        assertThat(orderService.cancel(5L, 1L)).isNull();
    }

    // ---- reads ----

    @Test
    void findAllWithUser_enrichesOrdersWithUserName() {
        Order order = order(1L, 1L, OrderStatus.PENDING);
        when(orderRepository.findAllByOrderByIdDesc()).thenReturn(List.of(order));
        when(userClient.getName(1L)).thenReturn("Alice");
        when(userClient.getEmail(1L)).thenReturn("alice@example.com");

        var result = orderService.findAllWithUser();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userName()).isEqualTo("Alice");
        assertThat(result.get(0).userEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findByUserId_delegatesToRepository() {
        List<Order> orders = List.of(order(1L, 1L, OrderStatus.PENDING));
        when(orderRepository.findAllByUserIdOrderByIdDesc(1L)).thenReturn(orders);

        assertThat(orderService.findByUserId(1L)).isEqualTo(orders);
    }
}