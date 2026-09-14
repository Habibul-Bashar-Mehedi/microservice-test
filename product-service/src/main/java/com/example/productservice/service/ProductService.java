package com.example.productservice.service;

import com.example.productservice.entity.Product;
import com.example.productservice.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public Product create(Product product) {
        return productRepository.save(product);
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    public Product updateQuantity(Long id, Integer quantity) {
        Product product = productRepository.findById(id).orElse(null);

        if (product == null) {
            return null;
        }

        product.setAvailableQuantity(
                product.getAvailableQuantity() - quantity
        );
        return productRepository.save(product);
    }
}