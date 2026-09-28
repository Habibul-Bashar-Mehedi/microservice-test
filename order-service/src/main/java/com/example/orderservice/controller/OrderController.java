package com.example.orderservice.controller;

import com.example.orderservice.entity.Order;
import com.example.orderservice.service.AsyncOrderService;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final AsyncOrderService asyncOrderService;

    @PostMapping("/v1/orders")
    public ResponseEntity<Order> createV1(@Valid @RequestBody Order order) {
        Order saved = orderService.create(order);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @GetMapping("/v1/orders")
    public List<Order> findAllV1() {
        return orderService.findAll();
    }

    @PostMapping("/v1/orders/{id}/confirm")
    public ResponseEntity<Order> confirmV1(@PathVariable Long id) {
        Order confirmed = orderService.confirm(id);

        return confirmed == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(confirmed);
    }

    @GetMapping("/v1/orders/{id}")
    public Order findByIdV1(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @PostMapping("/v2/orders")
    public ResponseEntity<Order> createV2(@Valid @RequestBody Order order) {
        Order saved = asyncOrderService.create(order);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PostMapping("/v2/orders/{id}/confirm")
    public ResponseEntity<Order> confirmV2(@PathVariable Long id) {
        Order confirmed = asyncOrderService.confirm(id);

        return confirmed == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.accepted().body(confirmed);
    }

    }
