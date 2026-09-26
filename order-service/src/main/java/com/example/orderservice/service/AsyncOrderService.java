package com.example.orderservice.service;

import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.publisher.OrderEventPublisher;
import com.example.orderservice.repository.OrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AsyncOrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public Order create(Order order) {
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be a positive number"
            );
        }

        Order existing = orderRepository
                .findFirstByUserIdAndProductIdAndQuantityAndStatusIn(
                        order.getUserId(),
                        order.getProductId(),
                        order.getQuantity(),
                        List.of(OrderStatus.PENDING, OrderStatus.CONFIRMING)
                )
                .orElse(null);

        if (existing != null) {
            return existing;
        }

        order.setStatus(OrderStatus.PENDING);
        order.setProductUpdated(false);
        Order saved = orderRepository.save(order);
        eventPublisher.publishCreated(saved);
        return saved;
    }

    public Order confirm(Long id) {
        Order order = orderRepository.findById(id).orElse(null);

        if (order == null) {
            return null;
        }

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.REJECTED) {
            return order;
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMING);
            orderRepository.save(order);
            eventPublisher.publishConfirmed(order);
        }

        return order;
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public Order findById(Long id) {
        return orderRepository.findById(id).orElse(null);
    }
}