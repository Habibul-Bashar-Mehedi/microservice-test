package com.example.productservice.controller;

import com.example.productservice.entity.Product;
import com.example.productservice.service.ProductService;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product product) {
        Product saved = productService.create(product);
        return ResponseEntity.created(URI.create("/products/" + saved.getId())).body(saved);
    }

    @GetMapping
    public List<Product> findAll() {
        return productService.findAll();
    }

    @PutMapping("/{id}/quantity")
    public ResponseEntity<Product> updateQuantity(
            @PathVariable Long id,
            @RequestBody Integer quantity
    ) {
        Product updated = productService.updateQuantity(id, quantity);

        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}")
    public Product findById(@PathVariable Long id) {
        return productService.findById(id);
    }
}
