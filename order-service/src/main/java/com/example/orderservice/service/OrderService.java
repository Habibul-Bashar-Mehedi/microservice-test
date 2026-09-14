package com.example.orderservice.service;

import com.example.orderservice.entity.Order;
import com.example.orderservice.repository.OrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestClient restClient;

    public Order create(Order order) {
        restClient.put()
                .uri(
                        "http://localhost:8082/products/{id}/quantity",
                        order.getProductId()
                )
                .body(order.getQuantity())
                .retrieve()
                .toBodilessEntity();

        return orderRepository.save(order);
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public Order findById(Long id) {
        return orderRepository.findById(id).orElse(null);
    }
}
