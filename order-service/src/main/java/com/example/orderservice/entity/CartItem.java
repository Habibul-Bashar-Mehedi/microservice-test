package com.example.orderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "User ID is required")
    @Min(value = 1, message = "User ID must be positive")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull(message = "Product ID is required")
    @Min(value = 1, message = "Product ID must be positive")
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "name")
    private String name;

    @Column(name = "price")
    private BigDecimal price;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be positive")
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "available_quantity")
    private Integer availableQuantity;
}
