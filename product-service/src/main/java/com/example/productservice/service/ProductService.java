package com.example.productservice.service;

import com.example.productservice.entity.Product;
import com.example.productservice.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @CacheEvict(value = {"products"}, allEntries = true)
    public Product create(Product product) {
        try {
            return productRepository.save(product);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product with name " + product.getName() + " already exists"
            );
        }
    }

    @Cacheable("products")
    public List<Product> findAll() {
        return productRepository.findAllByOrderByIdDesc();
    }

    @Cacheable("productById")
    public Product findById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    @Transactional
    @CacheEvict(value = {"products", "productById"}, allEntries = true)
    public Product updateQuantity(Long id, Integer quantity) {
        Product product = productRepository.findByIdForUpdate(id).orElse(null);

        if (product == null) {
            return null;
        }

        int remaining = product.getAvailableQuantity() - quantity;

        if (remaining < 0) {
            throw new InsufficientStockException(
                    "Insufficient stock for product " + id
                            + ": available " + product.getAvailableQuantity() + ", requested " + quantity
            );
        }

        product.setAvailableQuantity(remaining);
        return productRepository.save(product);
    }
}