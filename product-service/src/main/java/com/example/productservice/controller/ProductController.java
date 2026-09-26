package com.example.productservice.controller;

import com.example.productservice.entity.Product;
import com.example.productservice.service.InsufficientStockException;
import com.example.productservice.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    @PostMapping("/v1/products")
    public ResponseEntity<Product> createV1(@Valid @RequestBody Product product) {
        Product saved = productService.create(product);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @GetMapping("/v1/products")
    public List<Product> findAllV1() {
        return productService.findAll();
    }

    @PutMapping("/v1/products/{id}/quantity")
    public ResponseEntity<Product> updateQuantityV1(
            @PathVariable Long id,
            @RequestBody @Min(value = 1, message = "Quantity must be positive") Integer quantity
    ) {
        Product updated = productService.updateQuantity(id, quantity);

        return updated == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(updated);
    }

    @GetMapping("/v1/products/{id}")
    public Product findByIdV1(@PathVariable Long id) {
        return productService.findById(id);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<String> insufficientStock(InsufficientStockException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> duplicateProduct(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Product with that name already exists");
    }
}
