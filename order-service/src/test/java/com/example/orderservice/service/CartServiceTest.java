package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.orderservice.entity.CartItem;
import com.example.orderservice.entity.Order;
import com.example.orderservice.repository.CartItemRepository;
import java.math.BigDecimal;
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
class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private OrderService orderService;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartItemRepository, orderService);
    }

    private CartItem item(long productId, int quantity) {
        return CartItem.builder()
                .userId(1L)
                .productId(productId)
                .name("P" + productId)
                .price(new BigDecimal("10.00"))
                .quantity(quantity)
                .availableQuantity(10)
                .build();
    }

    @Test
    void add_newItem_saves() {
        when(cartItemRepository.findByUserIdAndProductId(1L, 2L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        CartItem saved = cartService.add(item(2L, 3));

        assertThat(saved.getQuantity()).isEqualTo(3);
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void add_existingItem_mergesAndClampsToAvailable() {
        CartItem existing = item(2L, 8);
        when(cartItemRepository.findByUserIdAndProductId(1L, 2L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        CartItem saved = cartService.add(item(2L, 5));

        assertThat(saved.getQuantity()).isEqualTo(10);
    }

    @Test
    void add_invalidQuantity_throwsBadRequest() {
        assertThatThrownBy(() -> cartService.add(CartItem.builder().userId(1L).productId(2L).quantity(0).build()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void setQuantity_updates() {
        when(cartItemRepository.findByUserIdAndProductId(1L, 2L)).thenReturn(Optional.of(item(2L, 1)));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(cartService.setQuantity(1L, 2L, 4).getQuantity()).isEqualTo(4);
    }

    @Test
    void setQuantity_notFound_throws() {
        when(cartItemRepository.findByUserIdAndProductId(1L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.setQuantity(1L, 9L, 1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void removeAndClear_delegate() {
        cartService.remove(1L, 2L);
        cartService.clear(1L);

        verify(cartItemRepository).deleteByUserIdAndProductId(1L, 2L);
        verify(cartItemRepository).deleteAllByUserId(1L);
    }

    @Test
    void checkout_createsOrderPerItemAndClearsCart() {
        when(cartItemRepository.findAllByUserIdOrderByIdAsc(1L)).thenReturn(List.of(item(2L, 3), item(4L, 1)));
        when(orderService.create(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Order> orders = cartService.checkout(1L);

        assertThat(orders).hasSize(2);
        verify(cartItemRepository).deleteAllByUserId(1L);
    }

    @Test
    void checkout_emptyCart_throwsConflict() {
        when(cartItemRepository.findAllByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> cartService.checkout(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
