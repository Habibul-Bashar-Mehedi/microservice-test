package com.example.orderservice.controller;

import com.example.orderservice.entity.CartItem;
import com.example.orderservice.entity.Order;
import com.example.orderservice.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/v1/cart")
    public List<CartItem> list(@RequestParam Long userId) {
        return cartService.list(userId);
    }

    @PostMapping("/v1/cart/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartItem add(@Valid @RequestBody CartItem item) {
        return cartService.add(item);
    }

    @PutMapping("/v1/cart/items/{productId}")
    public CartItem setQuantity(
            @PathVariable Long productId,
            @RequestParam Long userId,
            @RequestParam @Min(1) Integer quantity) {
        return cartService.setQuantity(userId, productId, quantity);
    }

    @DeleteMapping("/v1/cart/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long productId, @RequestParam Long userId) {
        cartService.remove(userId, productId);
    }

    @DeleteMapping("/v1/cart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@RequestParam Long userId) {
        cartService.clear(userId);
    }

    @PostMapping("/v1/cart/checkout")
    public List<Order> checkout(@RequestParam Long userId) {
        return cartService.checkout(userId);
    }
}
