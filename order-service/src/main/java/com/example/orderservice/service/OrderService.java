package com.example.orderservice.service;

import com.example.orderservice.client.ProductClient;
import com.example.orderservice.client.UserClient;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final ProductClient productClient;

    public Order create(Order order) {
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be a positive number"
            );
        }

        if (!userClient.isActive(order.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User " + order.getUserId() + " is not active"
            );
        }

        Order existing = findDuplicate(order);
        if (existing != null) {
            return existing;
        }

        order.setStatus(OrderStatus.PENDING);
        order.setProductUpdated(false);
        return orderRepository.save(order);
    }

    private Order findDuplicate(Order order) {
        return orderRepository
                .findFirstByUserIdAndProductIdAndQuantityAndStatusIn(
                        order.getUserId(),
                        order.getProductId(),
                        order.getQuantity(),
                        List.of(OrderStatus.PENDING, OrderStatus.CONFIRMING)
                )
                .orElse(null);
    }

    public Order confirm(Long id) {
        Order order = orderRepository.findById(id).orElse(null);

        if (order == null) {
            return null;
        }

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return order;
        }

        productClient.updateQuantity(order.getProductId(), order.getQuantity());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setProductUpdated(true);

        return orderRepository.save(order);
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public Order findById(Long id) {
        return orderRepository.findById(id).orElse(null);
    }
}