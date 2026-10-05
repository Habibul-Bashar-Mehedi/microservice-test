package com.example.productservice.service;

import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductStatus;
import com.example.productservice.repository.ProductRepository;
import java.math.BigDecimal;
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
    private final ProductSearchService productSearchService;
    private final ProductNotificationService productNotificationService;

    @CacheEvict(value = {"products", "productById", "productsApproved"}, allEntries = true)
    public Product create(Product product, String managerEmail) {
        product.setStatus(ProductStatus.PENDING_MAINTAINER);
        product.setCreatedBy(managerEmail);
        product.setRejectionReason(null);
        product.setRejectedByRole(null);
        product.setMaintainerReviewer(null);
        product.setAdminReviewer(null);
        try {
            Product saved = productRepository.save(product);
            productSearchService.index(saved);
            return saved;
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

    @Cacheable("productsApproved")
    public List<Product> findAllApproved() {
        return productRepository.findByStatusOrderByIdDesc(ProductStatus.APPROVED);
    }

    public List<Product> findByStatus(ProductStatus status) {
        return productRepository.findByStatusOrderByIdDesc(status);
    }

    public List<Product> findByCreatedBy(String email) {
        return productRepository.findByCreatedByOrderByIdDesc(email);
    }

    @Cacheable("productById")
    public Product findById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    @Transactional
    @CacheEvict(value = {"products", "productById", "productsApproved"}, allEntries = true)
    public Product resubmit(Long id, Product updated, String managerEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found: " + id));

        if (!managerEmail.equalsIgnoreCase(product.getCreatedBy())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You can only resubmit your own products");
        }
        if (product.getStatus() != ProductStatus.REJECTED_BY_MAINTAINER
                && product.getStatus() != ProductStatus.REJECTED_BY_ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only rejected products can be resubmitted");
        }

        try {
            product.setName(updated.getName());
            product.setPrice(updated.getPrice());
            product.setAvailableQuantity(updated.getAvailableQuantity());
            product.setStatus(ProductStatus.PENDING_MAINTAINER);
            product.setRejectionReason(null);
            product.setRejectedByRole(null);
            Product saved = productRepository.save(product);
            productSearchService.index(saved);
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product with name " + updated.getName() + " already exists"
            );
        }
    }

    @Transactional
    @CacheEvict(value = {"products", "productById", "productsApproved"}, allEntries = true)
    public Product maintainerReview(Long id, String maintainerEmail, boolean approved, String reason) {
        Product product = requireStatus(id, ProductStatus.PENDING_MAINTAINER);
        product.setMaintainerReviewer(maintainerEmail);

        if (approved) {
            product.setStatus(ProductStatus.PENDING_ADMIN);
            product.setRejectionReason(null);
            product.setRejectedByRole(null);
            Product saved = productRepository.save(product);
            productNotificationService.notify(null, "ADMIN", saved.getId(), saved.getName(),
                    "Product '" + saved.getName() + "' was accepted by maintainer " + maintainerEmail
                            + " and is awaiting your final approval.");
            productSearchService.index(saved);
            return saved;
        }

        requireReason(reason);
        product.setStatus(ProductStatus.REJECTED_BY_MAINTAINER);
        product.setRejectionReason(reason);
        product.setRejectedByRole("MAINTAINER");
        Product saved = productRepository.save(product);
        productNotificationService.notify(saved.getCreatedBy(), null, saved.getId(), saved.getName(),
                "Product '" + saved.getName() + "' was rejected by maintainer " + maintainerEmail
                        + ". Reason: " + reason);
        productSearchService.index(saved);
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"products", "productById", "productsApproved"}, allEntries = true)
    public Product adminReview(Long id, String adminEmail, boolean approved, String reason) {
        Product product = requireStatus(id, ProductStatus.PENDING_ADMIN);
        product.setAdminReviewer(adminEmail);

        if (approved) {
            product.setStatus(ProductStatus.APPROVED);
            product.setRejectionReason(null);
            product.setRejectedByRole(null);
            Product saved = productRepository.save(product);
            notifyManagerAndMaintainer(saved, "Product '" + saved.getName()
                    + "' was finally approved by admin " + adminEmail + ".");
            productSearchService.index(saved);
            return saved;
        }

        requireReason(reason);
        product.setStatus(ProductStatus.REJECTED_BY_ADMIN);
        product.setRejectionReason(reason);
        product.setRejectedByRole("ADMIN");
        Product saved = productRepository.save(product);
        notifyManagerAndMaintainer(saved, "Product '" + saved.getName()
                + "' was rejected by admin " + adminEmail + ". Reason: " + reason);
        productSearchService.index(saved);
        return saved;
    }

    private void notifyManagerAndMaintainer(Product product, String message) {
        productNotificationService.notify(product.getCreatedBy(), null,
                product.getId(), product.getName(), message);
        productNotificationService.notify(product.getMaintainerReviewer(), null,
                product.getId(), product.getName(), message);
    }

    private Product requireStatus(Long id, ProductStatus expected) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found: " + id));

        if (product.getStatus() != expected) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product " + id + " is " + product.getStatus() + ", expected " + expected);
        }
        return product;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }
    }

    @Transactional
    @CacheEvict(value = {"products", "productById", "productsApproved"}, allEntries = true)
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
        Product saved = productRepository.save(product);
        productSearchService.index(saved);
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"products", "productById", "productsApproved"}, allEntries = true)
    public Product addQuantity(Long id, Integer quantity) {
        Product product = productRepository.findByIdForUpdate(id).orElse(null);

        if (product == null) {
            return null;
        }

        product.setAvailableQuantity(product.getAvailableQuantity() + quantity);
        Product saved = productRepository.save(product);
        productSearchService.index(saved);
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"products", "productById", "productsApproved"}, allEntries = true)
    public Product updatePrice(Long id, BigDecimal price) {
        Product product = productRepository.findById(id).orElse(null);

        if (product == null) {
            return null;
        }

        product.setPrice(price);
        Product saved = productRepository.save(product);
        productSearchService.index(saved);
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"products", "productById", "productsApproved"}, allEntries = true)
    public Product updateName(Long id, String name) {
        Product product = productRepository.findById(id).orElse(null);

        if (product == null) {
            return null;
        }

        try {
            product.setName(name);
            Product saved = productRepository.save(product);
            productSearchService.index(saved);
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product with name " + name + " already exists"
            );
        }
    }
}