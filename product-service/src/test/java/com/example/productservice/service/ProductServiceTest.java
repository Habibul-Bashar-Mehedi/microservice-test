package com.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
                .createdBy("manager@example.com")
                .maintainerReviewer("maintainer@example.com")
                .build();
    }

    private void stubSave() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- create ----

    @Test
    void create_startsPendingMaintainerAndRecordsManager() {
        stubSave();
        Product request = Product.builder().name("Phone").price(new BigDecimal("10.00"))
                .availableQuantity(2).build();

        Product saved = productService.create(request, "manager@example.com");

        assertThat(saved.getStatus()).isEqualTo(ProductStatus.PENDING_MAINTAINER);
        assertThat(saved.getCreatedBy()).isEqualTo("manager@example.com");
        assertThat(saved.getRejectionReason()).isNull();
        verify(productSearchService).index(saved);
    }

    @Test
    void create_priceBetween100And200_isDirectlyApproved() {
        stubSave();
        Product request = Product.builder().name("Phone").price(new BigDecimal("150.00"))
                .availableQuantity(2).build();

        Product saved = productService.create(request, "manager@example.com");

        assertThat(saved.getStatus()).isEqualTo(ProductStatus.APPROVED);
        assertThat(saved.getCategory()).isEqualTo(Category.OTHER);
    }

    @Test
    void create_price200_isDirectlyApproved() {
        stubSave();
        Product request = Product.builder().name("Phone").price(new BigDecimal("200.00"))
                .availableQuantity(2).build();

        Product saved = productService.create(request, "manager@example.com");

        assertThat(saved.getStatus()).isEqualTo(ProductStatus.APPROVED);
    }

    @Test
    void create_priceBetween200And500_goesToMaintainer() {
        stubSave();
        Product request = Product.builder().name("Phone").price(new BigDecimal("350.00"))
                .availableQuantity(2).build();

        Product saved = productService.create(request, "manager@example.com");

        assertThat(saved.getStatus()).isEqualTo(ProductStatus.PENDING_MAINTAINER);
    }

    @Test
    void create_priceAbove500_goesToMaintainerBeforeAdmin() {
        stubSave();
        Product request = Product.builder().name("Phone").price(new BigDecimal("501.00"))
                .availableQuantity(2).build();

        Product saved = productService.create(request, "manager@example.com");

        assertThat(saved.getStatus()).isEqualTo(ProductStatus.PENDING_MAINTAINER);
    }

    @Test
    void create_priceBelow100_fallsBackToAdminArm() {
        stubSave();
        Product request = Product.builder().name("Phone").price(new BigDecimal("99.99"))
                .availableQuantity(2).build();

        Product saved = productService.create(request, "manager@example.com");

        assertThat(saved.getStatus()).isEqualTo(ProductStatus.PENDING_MAINTAINER);
    }

    @Test
    void create_stapleCategory_isDirectlyApprovedRegardlessOfHighPrice() {
        stubSave();
        Product request = Product.builder().name("Chal").price(new BigDecimal("900.00"))
                .availableQuantity(2).category(Category.CHAL).build();

        Product saved = productService.create(request, "manager@example.com");

        assertThat(saved.getStatus()).isEqualTo(ProductStatus.APPROVED);
    }

    @Test
    void create_maintainerOnlyCategory_goesToMaintainerRegardlessOfLowPrice() {
        stubSave();
        Product request = Product.builder().name("Chini").price(new BigDecimal("50.00"))
                .availableQuantity(2).category(Category.CHINI).build();

        Product saved = productService.create(request, "manager@example.com");

        assertThat(saved.getStatus()).isEqualTo(ProductStatus.PENDING_MAINTAINER);
    }

    // ---- maintainer review ----

    @Test
    void maintainerReview_accept_movesToPendingAdminAndNotifiesAdmin() {
        Product p = product(1L, ProductStatus.PENDING_MAINTAINER);
        p.setPrice(new BigDecimal("600.00"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.maintainerReview(1L, "maint@example.com", true, null);

        assertThat(result.getStatus()).isEqualTo(ProductStatus.PENDING_ADMIN);
        assertThat(result.getMaintainerReviewer()).isEqualTo("maint@example.com");
        verify(productNotificationService).notify(isNull(), eq("ADMIN"), eq(1L), eq("Phone"), anyString());
    }

    @Test
    void maintainerReview_accept_withoutAdminRequired_approvesDirectly() {
        Product p = product(1L, ProductStatus.PENDING_MAINTAINER);
        p.setPrice(new BigDecimal("300.00"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.maintainerReview(1L, "maint@example.com", true, null);

        assertThat(result.getStatus()).isEqualTo(ProductStatus.APPROVED);
        verify(productNotificationService).notify(eq("manager@example.com"), isNull(), eq(1L),
                eq("Phone"), anyString());
        verify(productNotificationService, never()).notify(isNull(), eq("ADMIN"), anyLong(), anyString(), anyString());
    }

    @Test
    void maintainerReview_maintainerOnlyCategory_approvesWithoutAdmin() {
        Product p = product(1L, ProductStatus.PENDING_MAINTAINER);
        p.setCategory(Category.MOSHLA);
        p.setPrice(new BigDecimal("900.00"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.maintainerReview(1L, "maint@example.com", true, null);

        assertThat(result.getStatus()).isEqualTo(ProductStatus.APPROVED);
        verify(productNotificationService, never()).notify(isNull(), eq("ADMIN"), anyLong(), anyString(), anyString());
    }

    @Test
    void maintainerReview_reject_requiresReason() {
        Product p = product(1L, ProductStatus.PENDING_MAINTAINER);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.maintainerReview(1L, "maint@example.com", false, "  "))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(productRepository, never()).save(any());
    }

    @Test
    void maintainerReview_reject_returnsToManagerWithReason() {
        Product p = product(1L, ProductStatus.PENDING_MAINTAINER);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.maintainerReview(1L, "maint@example.com", false, "Too cheap");

        assertThat(result.getStatus()).isEqualTo(ProductStatus.REJECTED_BY_MAINTAINER);
        assertThat(result.getRejectionReason()).isEqualTo("Too cheap");
        assertThat(result.getRejectedByRole()).isEqualTo("MAINTAINER");
        verify(productNotificationService).notify(eq("manager@example.com"), isNull(), eq(1L), eq("Phone"), anyString());
    }

    @Test
    void maintainerReview_wrongStatus_conflict() {
        Product p = product(1L, ProductStatus.PENDING_ADMIN);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.maintainerReview(1L, "maint@example.com", true, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void review_productNotFound() {
        when(productRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.maintainerReview(5L, "maint@example.com", true, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- admin review ----

    @Test
    void adminReview_accept_finallyApproves() {
        Product p = product(1L, ProductStatus.PENDING_ADMIN);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.adminReview(1L, "admin@example.com", true, null);

        assertThat(result.getStatus()).isEqualTo(ProductStatus.APPROVED);
        assertThat(result.getAdminReviewer()).isEqualTo("admin@example.com");
        verify(productNotificationService).notify(eq("manager@example.com"), isNull(), anyLong(), anyString(), anyString());
        verify(productNotificationService).notify(eq("maintainer@example.com"), isNull(), anyLong(), anyString(), anyString());
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
    void adminReview_reject_notifiesManagerAndMaintainer() {
        Product p = product(1L, ProductStatus.PENDING_ADMIN);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product result = productService.adminReview(1L, "admin@example.com", false, "Missing docs");

        assertThat(result.getStatus()).isEqualTo(ProductStatus.REJECTED_BY_ADMIN);
        assertThat(result.getRejectedByRole()).isEqualTo("ADMIN");
        verify(productNotificationService).notify(eq("manager@example.com"), isNull(), anyLong(), anyString(), anyString());
        verify(productNotificationService).notify(eq("maintainer@example.com"), isNull(), anyLong(), anyString(), anyString());
    }

    // ---- resubmit ----

    @Test
    void resubmit_movesRejectedProductBackToMaintainer() {
        Product p = product(1L, ProductStatus.REJECTED_BY_ADMIN);
        p.setRejectionReason("nope");
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        stubSave();

        Product updated = Product.builder().name("Phone v2").price(new BigDecimal("300.00"))
                .availableQuantity(20).build();

        Product result = productService.resubmit(1L, updated, "manager@example.com");

        assertThat(result.getStatus()).isEqualTo(ProductStatus.PENDING_MAINTAINER);
        assertThat(result.getName()).isEqualTo("Phone v2");
        assertThat(result.getRejectionReason()).isNull();
    }

    @Test
    void resubmit_onlyOwnProductsAllowed() {
        Product p = product(1L, ProductStatus.REJECTED_BY_MAINTAINER);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.resubmit(1L,
                Product.builder().name("x").price(BigDecimal.ONE).availableQuantity(1).build(),
                "other@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void resubmit_onlyRejectedProductsAllowed() {
        Product p = product(1L, ProductStatus.PENDING_MAINTAINER);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.resubmit(1L,
                Product.builder().name("x").price(BigDecimal.ONE).availableQuantity(1).build(),
                "manager@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
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
                "manager@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsNonPositivePrice() {
        assertThatThrownBy(() -> productService.create(
                Product.builder().name("X").price(BigDecimal.ZERO).availableQuantity(1).build(),
                "manager@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsNegativeQuantity() {
        assertThatThrownBy(() -> productService.create(
                Product.builder().name("X").price(BigDecimal.ONE).availableQuantity(-1).build(),
                "manager@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsNullName() {
        assertThatThrownBy(() -> productService.create(
                Product.builder().price(BigDecimal.ONE).availableQuantity(1).build(),
                "manager@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsNullPrice() {
        assertThatThrownBy(() -> productService.create(
                Product.builder().name("X").availableQuantity(1).build(),
                "manager@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsNullQuantity() {
        assertThatThrownBy(() -> productService.create(
                Product.builder().name("X").price(BigDecimal.ONE).build(),
                "manager@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void resubmit_rejectsInvalidInput() {
        Product p = product(1L, ProductStatus.REJECTED_BY_MAINTAINER);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.resubmit(1L,
                Product.builder().name("").price(BigDecimal.ONE).availableQuantity(1).build(),
                "manager@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}