package com.example.productservice.controller;

import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductNotification;
import com.example.productservice.entity.ProductStatus;
import com.example.productservice.service.InsufficientStockException;
import com.example.productservice.service.ProductNotificationService;
import com.example.productservice.service.ProductSearchService;
import com.example.productservice.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;
    private final ProductNotificationService productNotificationService;
    private final ProductSearchService productSearchService;

    @PostMapping("/v1/products")
    public ResponseEntity<Product> createV1(@Valid @RequestBody Product product, Authentication authentication) {
        Product saved = productService.create(product, authentication.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @GetMapping("/v1/products")
    public List<Product> findAllV1() {
        return productService.findAllApproved();
    }

    @GetMapping("/v1/products/all")
    public List<Product> findAllForAdminV1() {
        return productService.findAll();
    }

    @GetMapping("/v1/products/mine")
    public List<Product> findMineV1(Authentication authentication) {
        return productService.findByCreatedBy(authentication.getName());
    }

    @GetMapping("/v1/products/pending/manager")
    public List<Product> pendingManagerV1() {
        return productService.findByStatus(ProductStatus.PENDING_MANAGER);
    }

    @GetMapping("/v1/products/pending/specialist")
    public List<Product> pendingSpecialistV1() {
        return productService.findByStatus(ProductStatus.PENDING_PRODUCT_SPECIALIST);
    }

    @GetMapping("/v1/products/pending/salesman")
    public List<Product> pendingSalesmanV1() {
        return productService.findByStatus(ProductStatus.PENDING_SALESMAN);
    }

    @GetMapping("/v1/products/pending/admin")
    public List<Product> pendingAdminV1() {
        return productService.findByStatus(ProductStatus.PENDING_ADMIN);
    }

    @PutMapping("/v1/products/{id}")
    public Product resubmitV1(@PathVariable Long id, @Valid @RequestBody Product product,
            Authentication authentication) {
        return productService.resubmit(id, product, authentication.getName());
    }

    @PostMapping("/v1/products/{id}/manager/review")
    public Product managerReviewV1(@PathVariable Long id, @RequestBody ReviewRequest request,
            Authentication authentication) {
        return productService.managerReview(id, authentication.getName(), request.approved(), request.reason());
    }

    @PostMapping("/v1/products/{id}/specialist/review")
    public Product specialistReviewV1(@PathVariable Long id, @RequestBody ReviewRequest request,
            Authentication authentication) {
        return productService.specialistReview(id, authentication.getName(), request.approved(), request.reason());
    }

    @PostMapping("/v1/products/{id}/salesman/review")
    public Product salesmanReviewV1(@PathVariable Long id, @RequestBody ReviewRequest request,
            Authentication authentication) {
        return productService.salesmanReview(id, authentication.getName(), request.approved(), request.reason());
    }

    @PostMapping("/v1/products/{id}/admin/review")
    public Product adminReviewV1(@PathVariable Long id, @RequestBody ReviewRequest request,
            Authentication authentication) {
        return productService.adminReview(id, authentication.getName(), request.approved(), request.reason());
    }

    @GetMapping("/v1/notifications")
    public List<ProductNotification> notificationsV1(Authentication authentication) {
        return productNotificationService.findByRecipient(authentication.getName(), currentRole(authentication));
    }

    @PostMapping("/v1/notifications/{id}/read")
    public void markNotificationReadV1(@PathVariable Long id) {
        productNotificationService.markRead(id);
    }

    @GetMapping("/v1/products/search")
    public List<Product> searchV1(@RequestParam(name = "q", defaultValue = "") String q) {
        if (q == null || q.isBlank()) {
            return productService.findAllApproved();
        }
        return productSearchService.search(q.trim())
                .stream()
                .map(doc -> Product.builder()
                        .id(doc.getId())
                        .name(doc.getName())
                        .price(doc.getPrice())
                        .availableQuantity(doc.getAvailableQuantity())
                        .build())
                .toList();
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

    @PutMapping("/v1/products/{id}/add-quantity")
    public ResponseEntity<Product> addQuantityV1(
            @PathVariable Long id,
            @RequestBody @Min(value = 1, message = "Quantity must be positive") Integer quantity
    ) {
        Product updated = productService.addQuantity(id, quantity);

        return updated == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(updated);
    }

    @PutMapping("/v1/products/{id}/price")
    public ResponseEntity<Product> updatePriceV1(
            @PathVariable Long id,
            @RequestBody @DecimalMin(value = "0.01", message = "Price must be greater than 0") BigDecimal price
    ) {
        Product updated = productService.updatePrice(id, price);

        return updated == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(updated);
    }

    @PutMapping("/v1/products/{id}/name")
    public ResponseEntity<Product> updateNameV1(
            @PathVariable Long id,
            @RequestBody @NotBlank(message = "Name must not be blank") String name
    ) {
        Product updated = productService.updateName(id, name);

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

    private String currentRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("USER");
    }

    public record ReviewRequest(boolean approved, String reason) {
    }
}