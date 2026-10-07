package com.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.productservice.entity.Category;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductStatus;
import com.example.productservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductSearchService productSearchService;
    @Mock
    private ProductNotificationService productNotificationService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, productSearchService, productNotificationService);
    }

    private Product product(Long id, ProductStatus status) {
        return Product.builder()
                .id(id)
                .name("Phone")
                .price(new BigDecimal("100.00"))
                .availableQuantity(10)
                .status(status)
                .createdBy("maintainer@example.com")
                .managerReviewer("manager@example.com")
                .specialistReviewer("specialist@example.com")
                .salesmanReviewer("salesman@example.com")
                .adminReviewer("admin@example.com")
                .build();
    }

    private void stubSave() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- create ----

    @Test
    void create_startsPendingManagerAndRecordsMaintainer() {
        stubSave();
        Product request = Product.builder().name("Phone").price(new BigDecimal("10.00"))
                .availableQuantity(2).build();

        Product saved = productService.create(request, "maintainer@example.com");

        assertThat(saved.getStatus()).isEqualTo(ProductStatus.PENDING_MANAGER);
        assertThat(saved.getCreatedBy()).isEqualTo("maintainer@example.com");
        assertThat(saved.getRejectionReason()).isNull();
        assertThat(saved.getManagerReviewer()).isNull();
        assertThat(saved.getSpecialistReviewer()).isNull();
        assertThat(saved.getSalesmanReviewer()).isNull();
        assertThat(saved.getAdminReviewer()).isNull();
        verify(productSearchService).index(saved);
    }

    @Test
    void create_defaultsCategoryToOther() {
        stubSave();
        Product request = Product.builder().name("Phone").price(new BigDecimal("10.00"))
                .availableQuantity(2).build();

        Product saved = productService.create(request, "maintainer@example.com");

        assertThat(saved.getCategory()).isEqualTo(Category.OTHER);
    }

    // ---- manager review ----

    @Test
    void managerReview_accept_movesToSpecialistAndNotifiesSpecialistRole() {
        Product p = product(1L, ProductStatus.PENDING_MANAGER);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.managerReview(1L, "manager@example.com", true, null);

        assertThat(result.getStatus()).isEqualTo(ProductStatus.PENDING_PRODUCT_SPECIALIST);
        assertThat(result.getManagerReviewer()).isEqualTo("manager@example.com");
        verify(productNotificationService).notify(isNull(), eq("PRODUCT_SPECIALIST"), eq(1L),
                eq("Phone"), anyString());
    }

    @Test
    void managerReview_reject_requiresReason() {
        Product p = product(1L, ProductStatus.PENDING_MANAGER);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.managerReview(1L, "manager@example.com", false, "  "))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(productRepository, never()).save(any());
    }

    @Test
    void managerReview_reject_notifiesMaintainer() {
        Product p = product(1L, ProductStatus.PENDING_MANAGER);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.managerReview(1L, "manager@example.com", false, "Bad data");

        assertThat(result.getStatus()).isEqualTo(ProductStatus.REJECTED_BY_MANAGER);
        assertThat(result.getRejectionReason()).isEqualTo("Bad data");
        assertThat(result.getRejectedByRole()).isEqualTo("MANAGER");
        verify(productNotificationService).notify(eq("maintainer@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
    }

    @Test
    void managerReview_wrongStatus_conflict() {
        Product p = product(1L, ProductStatus.PENDING_ADMIN);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.managerReview(1L, "manager@example.com", true, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void review_productNotFound() {
        when(productRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.managerReview(5L, "manager@example.com", true, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- product specialist review ----

    @Test
    void specialistReview_accept_movesToSalesmanAndNotifiesSalesmanRole() {
        Product p = product(1L, ProductStatus.PENDING_PRODUCT_SPECIALIST);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.specialistReview(1L, "specialist@example.com", true, null);

        assertThat(result.getStatus()).isEqualTo(ProductStatus.PENDING_SALESMAN);
        assertThat(result.getSpecialistReviewer()).isEqualTo("specialist@example.com");
        verify(productNotificationService).notify(isNull(), eq("SALESMAN"), eq(1L), eq("Phone"), anyString());
    }

    @Test
    void specialistReview_reject_notifiesMaintainerAndManager() {
        Product p = product(1L, ProductStatus.PENDING_PRODUCT_SPECIALIST);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.specialistReview(1L, "specialist@example.com", false, "Wrong spec");

        assertThat(result.getStatus()).isEqualTo(ProductStatus.REJECTED_BY_PRODUCT_SPECIALIST);
        assertThat(result.getRejectedByRole()).isEqualTo("PRODUCT_SPECIALIST");
        verify(productNotificationService).notify(eq("maintainer@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
        verify(productNotificationService).notify(eq("manager@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
    }

    @Test
    void specialistReview_reject_requiresReason() {
        Product p = product(1L, ProductStatus.PENDING_PRODUCT_SPECIALIST);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.specialistReview(1L, "specialist@example.com", false, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---- salesman review ----

    @Test
    void salesmanReview_accept_movesToAdminAndNotifiesAdminRole() {
        Product p = product(1L, ProductStatus.PENDING_SALESMAN);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.salesmanReview(1L, "salesman@example.com", true, null);

        assertThat(result.getStatus()).isEqualTo(ProductStatus.PENDING_ADMIN);
        assertThat(result.getSalesmanReviewer()).isEqualTo("salesman@example.com");
        verify(productNotificationService).notify(isNull(), eq("ADMIN"), eq(1L), eq("Phone"), anyString());
    }

    @Test
    void salesmanReview_reject_notifiesMaintainerManagerAndSpecialist() {
        Product p = product(1L, ProductStatus.PENDING_SALESMAN);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.salesmanReview(1L, "salesman@example.com", false, "No demand");

        assertThat(result.getStatus()).isEqualTo(ProductStatus.REJECTED_BY_SALESMAN);
        assertThat(result.getRejectedByRole()).isEqualTo("SALESMAN");
        verify(productNotificationService).notify(eq("maintainer@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
        verify(productNotificationService).notify(eq("manager@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
        verify(productNotificationService).notify(eq("specialist@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
    }

    // ---- admin review ----

    @Test
    void adminReview_accept_finallyApprovesAndNotifiesChain() {
        Product p = product(1L, ProductStatus.PENDING_ADMIN);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.adminReview(1L, "admin@example.com", true, null);

        assertThat(result.getStatus()).isEqualTo(ProductStatus.APPROVED);
        assertThat(result.getAdminReviewer()).isEqualTo("admin@example.com");
        verify(productNotificationService).notify(eq("maintainer@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
        verify(productNotificationService).notify(eq("manager@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
        verify(productNotificationService).notify(eq("specialist@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
        verify(productNotificationService).notify(eq("salesman@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
    }

    @Test
    void adminReview_reject_requiresReason() {
        Product p = product(1L, ProductStatus.PENDING_ADMIN);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.adminReview(1L, "admin@example.com", false, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminReview_reject_notifiesWholeChain() {
        Product p = product(1L, ProductStatus.PENDING_ADMIN);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.adminReview(1L, "admin@example.com", false, "Missing docs");

        assertThat(result.getStatus()).isEqualTo(ProductStatus.REJECTED_BY_ADMIN);
        assertThat(result.getRejectedByRole()).isEqualTo("ADMIN");
        verify(productNotificationService).notify(eq("maintainer@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
        verify(productNotificationService).notify(eq("manager@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
        verify(productNotificationService).notify(eq("specialist@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
        verify(productNotificationService).notify(eq("salesman@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
    }

    // ---- resubmit ----

    @Test
    void resubmit_fromManagerRejection_returnsToManager() {
        Product p = product(1L, ProductStatus.REJECTED_BY_MANAGER);
        p.setRejectionReason("nope");
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product updated = Product.builder().name("Phone v2").price(new BigDecimal("300.00"))
                .availableQuantity(20).build();

        Product result = productService.resubmit(1L, updated, "maintainer@example.com");

        assertThat(result.getStatus()).isEqualTo(ProductStatus.PENDING_MANAGER);
        assertThat(result.getName()).isEqualTo("Phone v2");
        assertThat(result.getRejectionReason()).isNull();
    }

    @Test
    void resubmit_fromSpecialistRejection_returnsToSpecialist() {
        Product p = product(1L, ProductStatus.REJECTED_BY_PRODUCT_SPECIALIST);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.resubmit(1L,
                Product.builder().name("Phone").price(BigDecimal.ONE).availableQuantity(1).build(),
                "maintainer@example.com");

        assertThat(result.getStatus()).isEqualTo(ProductStatus.PENDING_PRODUCT_SPECIALIST);
    }

    @Test
    void resubmit_fromSalesmanRejection_returnsToSalesman() {
        Product p = product(1L, ProductStatus.REJECTED_BY_SALESMAN);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.resubmit(1L,
                Product.builder().name("Phone").price(BigDecimal.ONE).availableQuantity(1).build(),
                "maintainer@example.com");

        assertThat(result.getStatus()).isEqualTo(ProductStatus.PENDING_SALESMAN);
    }

    @Test
    void resubmit_fromAdminRejection_returnsToAdmin() {
        Product p = product(1L, ProductStatus.REJECTED_BY_ADMIN);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.resubmit(1L,
                Product.builder().name("Phone").price(BigDecimal.ONE).availableQuantity(1).build(),
                "maintainer@example.com");

        assertThat(result.getStatus()).isEqualTo(ProductStatus.PENDING_ADMIN);
    }

    @Test
    void resubmit_onlyOwnProductsAllowed() {
        Product p = product(1L, ProductStatus.REJECTED_BY_MANAGER);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.resubmit(1L,
                Product.builder().name("x").price(BigDecimal.ONE).availableQuantity(1).build(),
                "other@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void resubmit_onlyRejectedProductsAllowed() {
        Product p = product(1L, ProductStatus.PENDING_MANAGER);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.resubmit(1L,
                Product.builder().name("x").price(BigDecimal.ONE).availableQuantity(1).build(),
                "maintainer@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void resubmit_rejectsInvalidInput() {
        Product p = product(1L, ProductStatus.REJECTED_BY_MANAGER);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.resubmit(1L,
                Product.builder().name("").price(BigDecimal.ONE).availableQuantity(1).build(),
                "maintainer@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---- stock ----

    @Test
    void updateQuantity_rejectsInsufficientStock() {
        Product p = product(1L, ProductStatus.APPROVED);
        p.setAvailableQuantity(3);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.updateQuantity(1L, 5))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void updateQuantity_decrementsWhenEnoughStock() {
        Product p = product(1L, ProductStatus.APPROVED);
        p.setAvailableQuantity(5);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.updateQuantity(1L, 2);

        assertThat(result.getAvailableQuantity()).isEqualTo(3);
    }

    @Test
    void updateQuantity_missingProductReturnsNull() {
        when(productRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThat(productService.updateQuantity(9L, 1)).isNull();
    }

    // ---- input validation ----

    @Test
    void create_rejectsBlankName() {
        assertThatThrownBy(() -> productService.create(
                Product.builder().name("  ").price(BigDecimal.ONE).availableQuantity(1).build(),
                "maintainer@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsNonPositivePrice() {
        assertThatThrownBy(() -> productService.create(
                Product.builder().name("X").price(BigDecimal.ZERO).availableQuantity(1).build(),
                "maintainer@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsNegativeQuantity() {
        assertThatThrownBy(() -> productService.create(
                Product.builder().name("X").price(BigDecimal.ONE).availableQuantity(-1).build(),
                "maintainer@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsNullName() {
        assertThatThrownBy(() -> productService.create(
                Product.builder().price(BigDecimal.ONE).availableQuantity(1).build(),
                "maintainer@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsNullPrice() {
        assertThatThrownBy(() -> productService.create(
                Product.builder().name("X").availableQuantity(1).build(),
                "maintainer@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsNullQuantity() {
        assertThatThrownBy(() -> productService.create(
                Product.builder().name("X").price(BigDecimal.ONE).build(),
                "maintainer@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
