package com.example.orderservice.repository;

import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findFirstByUserIdAndProductIdAndQuantityAndStatusIn(
            Long userId, Long productId, Integer quantity, Collection<OrderStatus> statuses);

    List<Order> findAllByOrderByIdDesc();
}