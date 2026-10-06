package com.example.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.service.AsyncOrderService;
import com.example.orderservice.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private AsyncOrderService asyncOrderService;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService, asyncOrderService);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Order order(Long id) {
        return Order.builder().id(id).userId(1L).productId(10L).quantity(2)
                .status(OrderStatus.PENDING).build();
    }

    @Test
    void createV1_returns201() {
        when(orderService.create(any(Order.class))).thenReturn(order(1L));
        assertThat(controller.createV1(order(null)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void findAllV1_delegatesToEnrichedList() {
        when(orderService.findAllWithUser()).thenReturn(List.of());
        assertThat(controller.findAllV1()).isEmpty();
    }

    @Test
    void confirmV1_okAndNotFound() {
        when(orderService.confirm(1L)).thenReturn(order(1L));
        when(orderService.confirm(9L)).thenReturn(null);

        assertThat(controller.confirmV1(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.confirmV1(9L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void findByIdAndByUser_delegate() {
        when(orderService.findById(1L)).thenReturn(order(1L));
        when(orderService.findByUserId(1L)).thenReturn(List.of(order(1L)));

        assertThat(controller.findByIdV1(1L)).isNotNull();
        assertThat(controller.findOrdersByUserV1(1L)).hasSize(1);
    }

    @Test
    void cancelV1_okAndNotFound() {
        when(orderService.cancel(1L, 1L)).thenReturn(order(1L));
        when(orderService.cancel(9L, 1L)).thenReturn(null);

        assertThat(controller.cancelV1(1L, 1L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.cancelV1(9L, 1L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createV2_returns201() {
        when(asyncOrderService.create(any(Order.class))).thenReturn(order(1L));
        assertThat(controller.createV2(order(null)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void confirmV2_returnsAcceptedOrNotFound() {
        when(asyncOrderService.confirm(1L)).thenReturn(order(1L));
        when(asyncOrderService.confirm(9L)).thenReturn(null);

        assertThat(controller.confirmV2(1L).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(controller.confirmV2(9L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createV3_returns201() {
        when(orderService.createV3(any(Order.class))).thenReturn(order(1L));
        assertThat(controller.createV3(order(null)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void confirmV3_okAndNotFound() {
        when(orderService.confirmV3(1L)).thenReturn(order(1L));
        when(orderService.confirmV3(9L)).thenReturn(null);

        assertThat(controller.confirmV3(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.confirmV3(9L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelV3_okAndNotFound() {
        when(orderService.cancel(1L, 1L)).thenReturn(order(1L));
        when(orderService.cancel(9L, 1L)).thenReturn(null);

        assertThat(controller.cancelV3(1L, 1L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.cancelV3(9L, 1L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}