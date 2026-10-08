package com.example.orderservice.service;

import com.example.orderservice.entity.CartItem;
import com.example.orderservice.entity.Order;
import com.example.orderservice.repository.CartItemRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final OrderService orderService;

    public List<CartItem> list(Long userId) {
        return cartItemRepository.findAllByUserIdOrderByIdAsc(userId);
    }

    @Transactional
    public CartItem add(CartItem item) {
        if (item.getQuantity() == null || item.getQuantity() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }

        CartItem existing = cartItemRepository
                .findByUserIdAndProductId(item.getUserId(), item.getProductId())
                .orElse(null);

        if (existing != null) {
            int cap = item.getAvailableQuantity() != null
                    ? Math.max(item.getAvailableQuantity(), 1)
                    : existing.getQuantity() + item.getQuantity();
            existing.setQuantity(Math.min(existing.getQuantity() + item.getQuantity(), cap));
            if (item.getName() != null) {
                existing.setName(item.getName());
            }
            if (item.getPrice() != null) {
                existing.setPrice(item.getPrice());
            }
            if (item.getAvailableQuantity() != null) {
                existing.setAvailableQuantity(item.getAvailableQuantity());
            }
            return cartItemRepository.save(existing);
        }
        return cartItemRepository.save(item);
    }

    @Transactional
    public CartItem setQuantity(Long userId, Long productId, Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
        }
        CartItem item = require(userId, productId);
        item.setQuantity(quantity);
        return cartItemRepository.save(item);
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        cartItemRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Transactional
    public void clear(Long userId) {
        cartItemRepository.deleteAllByUserId(userId);
    }

    @Transactional
    @CacheEvict(value = {"orders", "ordersWithUser"}, allEntries = true)
    public List<Order> checkout(Long userId) {
        List<CartItem> items = cartItemRepository.findAllByUserIdOrderByIdAsc(userId);
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty");
        }

        List<Order> orders = new ArrayList<>();
        for (CartItem item : items) {
            orders.add(orderService.create(Order.builder()
                    .userId(userId)
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .build()));
        }
        cartItemRepository.deleteAllByUserId(userId);
        return orders;
    }

    private CartItem require(Long userId, Long productId) {
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Cart item not found for product " + productId));
    }
}
