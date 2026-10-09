package com.example.productservice.service;

import com.example.productservice.entity.Category;
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
    public Product create(Product product, String maintainerEmail) {
        validateProductInput(product);
        product.setCategory(normalizeCategory(product.getCategory()));
        product.setStatus(ProductStatus.PENDING_MANAGER);
        product.setCreatedBy(maintainerEmail);
        product.setRejectionReason(null);
        product.setRejectedByRole(null);
        clearReviewers(product);
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
    public Product resubmit(Long id, Product updated, String maintainerEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found: " + id));

        if (!maintainerEmail.equalsIgnoreCase(product.getCreatedBy())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You can only resubmit your own products");
        }

        ProductStatus target = resubmitTarget(product.getStatus());
        if (target == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only rejected products can be resubmitted");
        }

        validateProductInput(updated);

        try {
            product.setName(updated.getName());
            product.setPrice(updated.getPrice());
            product.setAvailableQuantity(updated.getAvailableQuantity());
            if (updated.getCategory() != null) {
                product.setCategory(updated.getCategory());
            }
            product.setStatus(target);
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
    public Product managerReview(Long id, String managerEmail, boolean approved, String reason) {
        Product product = requireStatus(id, ProductStatus.PENDING_MANAGER);
        product.setManagerReviewer(managerEmail);

        if (approved) {
            product.setStatus(ProductStatus.PENDING_PRODUCT_SPECIALIST);
            product.setRejectionReason(null);
            product.setRejectedByRole(null);
            Product saved = productRepository.save(product);
            notifyRole("PRODUCT_SPECIALIST", saved,
                    "Product '" + saved.getName() + "' was accepted by manager " + managerEmail
                            + " and is awaiting your review.");
            productSearchService.index(saved);
            return saved;
        }

        requireReason(reason);
        Product saved = reject(product, ProductStatus.REJECTED_BY_MANAGER, "MANAGER", reason);
        notifyEmail(product.getCreatedBy(), saved,
                "Product '" + saved.getName() + "' was rejected by manager " + managerEmail
                        + ". Reason: " + reason);
        productSearchService.index(saved);
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"products", "productById", "productsApproved"}, allEntries = true)
    public Product specialistReview(Long id, String specialistEmail, boolean approved, String reason) {
        Product product = requireStatus(id, ProductStatus.PENDING_PRODUCT_SPECIALIST);
        product.setSpecialistReviewer(specialistEmail);

        if (approved) {
            product.setStatus(ProductStatus.PENDING_SALESMAN);
            product.setRejectionReason(null);
            product.setRejectedByRole(null);
            Product saved = productRepository.save(product);
            notifyRole("SALESMAN", saved,
                    "Product '" + saved.getName() + "' was accepted by product specialist "
                            + specialistEmail + " and is awaiting your review.");
            productSearchService.index(saved);
            return saved;
        }

        requireReason(reason);
        Product saved = reject(product, ProductStatus.REJECTED_BY_PRODUCT_SPECIALIST, "PRODUCT_SPECIALIST",
                reason);
        String message = "Product '" + saved.getName() + "' was rejected by product specialist "
                + specialistEmail + ". Reason: " + reason;
        notifyEmail(saved.getCreatedBy(), saved, message);
        notifyEmail(saved.getManagerReviewer(), saved, message);
        productSearchService.index(saved);
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"products", "productById", "productsApproved"}, allEntries = true)
    public Product salesmanReview(Long id, String salesmanEmail, boolean approved, String reason) {
        Product product = requireStatus(id, ProductStatus.PENDING_SALESMAN);
        product.setSalesmanReviewer(salesmanEmail);

        if (approved) {
            product.setStatus(ProductStatus.PENDING_ADMIN);
            product.setRejectionReason(null);
            product.setRejectedByRole(null);
            Product saved = productRepository.save(product);
            notifyRole("ADMIN", saved,
                    "Product '" + saved.getName() + "' was accepted by salesman " + salesmanEmail
                            + " and is awaiting your final review.");
            productSearchService.index(saved);
            return saved;
        }

        requireReason(reason);
        Product saved = reject(product, ProductStatus.REJECTED_BY_SALESMAN, "SALESMAN", reason);
        String message = "Product '" + saved.getName() + "' was rejected by salesman " + salesmanEmail
                + ". Reason: " + reason;
        notifyEmail(saved.getCreatedBy(), saved, message);
        notifyEmail(saved.getManagerReviewer(), saved, message);
        notifyEmail(saved.getSpecialistReviewer(), saved, message);
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
            String message = "Product '" + saved.getName() + "' was finally approved by admin "
                    + adminEmail + ".";
            notifyApprovalChain(saved, message);
            productSearchService.index(saved);
            return saved;
        }

        requireReason(reason);
        Product saved = reject(product, ProductStatus.REJECTED_BY_ADMIN, "ADMIN", reason);
        String message = "Product '" + saved.getName() + "' was rejected by admin " + adminEmail
                + ". Reason: " + reason;
        notifyApprovalChain(saved, message);
        productSearchService.index(saved);
        return saved;
    }

    private Product reject(Product product, ProductStatus status, String role, String reason) {
        product.setStatus(status);
        product.setRejectionReason(reason);
        product.setRejectedByRole(role);
        return productRepository.save(product);
    }

    private void notifyApprovalChain(Product product, String message) {
        notifyEmail(product.getCreatedBy(), product, message);
        notifyEmail(product.getManagerReviewer(), product, message);
        notifyEmail(product.getSpecialistReviewer(), product, message);
        notifyEmail(product.getSalesmanReviewer(), product, message);
    }

    private void notifyEmail(String email, Product product, String message) {
        if (email != null && !email.isBlank()) {
            productNotificationService.notify(email, null, product.getId(), product.getName(), message);
        }
    }

    private void notifyRole(String role, Product product, String message) {
        productNotificationService.notify(null, role, product.getId(), product.getName(), message);
    }

    private void clearReviewers(Product product) {
        product.setManagerReviewer(null);
        product.setSpecialistReviewer(null);
        product.setSalesmanReviewer(null);
        product.setAdminReviewer(null);
    }

    private ProductStatus resubmitTarget(ProductStatus status) {
        return switch (status) {
            case REJECTED_BY_MANAGER -> ProductStatus.PENDING_MANAGER;
            case REJECTED_BY_PRODUCT_SPECIALIST -> ProductStatus.PENDING_PRODUCT_SPECIALIST;
            case REJECTED_BY_SALESMAN -> ProductStatus.PENDING_SALESMAN;
            case REJECTED_BY_ADMIN -> ProductStatus.PENDING_ADMIN;
            default -> null;
        };
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

    private void validateProductInput(Product product) {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be greater than 0");
        }
        if (product.getAvailableQuantity() == null || product.getAvailableQuantity() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Available quantity cannot be negative");
        }
    }

    private Category normalizeCategory(Category category) {
        return category == null ? Category.OTHER : category;
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
    public Product updateImage(Long id, String imageUrl) {
        Product product = productRepository.findById(id).orElse(null);

        if (product == null) {
            return null;
        }

        product.setImageUrl(imageUrl);
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
