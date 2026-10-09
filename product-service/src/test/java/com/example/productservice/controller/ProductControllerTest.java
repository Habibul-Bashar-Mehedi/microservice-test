package com.example.productservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductNotification;
import com.example.productservice.entity.ProductStatus;
import com.example.productservice.service.ProductImageStorageService;
import com.example.productservice.service.ProductNotificationService;
import com.example.productservice.service.ProductSearchService;
import com.example.productservice.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;
    @Mock
    private ProductNotificationService productNotificationService;
    @Mock
    private ProductSearchService productSearchService;
    @Mock
    private ProductImageStorageService productImageStorageService;

    private ProductController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductController(productService, productNotificationService, productSearchService,
                productImageStorageService);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Authentication auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private Product product(Long id) {
        return Product.builder().id(id).name("Phone").price(new BigDecimal("10.00"))
                .availableQuantity(5).status(ProductStatus.PENDING_MANAGER).build();
    }

    @Test
    void createV1_returns201AndUsesAuthenticatedEmail() {
        when(productService.create(any(Product.class), eq("mt@x.com"))).thenReturn(product(1L));

        var response = controller.createV1(product(null), auth("mt@x.com", "MAINTAINER"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void createWithImageV1_storesImageAndCreatesProduct() {
        MockMultipartFile image = new MockMultipartFile("image", "p.png", "image/png", "bytes".getBytes());
        Product saved = product(1L);
        saved.setImageUrl("/v1/products/images/abc.png");
        when(productImageStorageService.store(image)).thenReturn("/v1/products/images/abc.png");
        when(productService.create(any(Product.class), eq("mt@x.com"))).thenReturn(saved);

        var response = controller.createWithImageV1(product(null), image, auth("mt@x.com", "MAINTAINER"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getImageUrl()).isEqualTo("/v1/products/images/abc.png");
    }

    @Test
    void createWithImageV1_deletesFileWhenCreationFails() {
        MockMultipartFile image = new MockMultipartFile("image", "p.png", "image/png", "bytes".getBytes());
        when(productImageStorageService.store(image)).thenReturn("/v1/products/images/abc.png");
        when(productService.create(any(Product.class), eq("mt@x.com")))
                .thenThrow(new RuntimeException("duplicate"));

        assertThatThrownBy(() -> controller.createWithImageV1(product(null), image, auth("mt@x.com", "MAINTAINER")))
                .isInstanceOf(RuntimeException.class);
        verify(productImageStorageService).delete("/v1/products/images/abc.png");
    }

    @Test
    void updateImageV1_ownerReplacesImageAndDeletesPrevious() {
        Product existing = product(1L);
        existing.setCreatedBy("mt@x.com");
        existing.setImageUrl("/v1/products/images/old.png");
        when(productService.findById(1L)).thenReturn(existing);
        MockMultipartFile image = new MockMultipartFile("image", "p.png", "image/png", "bytes".getBytes());
        when(productImageStorageService.store(image)).thenReturn("/v1/products/images/new.png");
        Product updated = product(1L);
        updated.setImageUrl("/v1/products/images/new.png");
        when(productService.updateImage(1L, "/v1/products/images/new.png")).thenReturn(updated);

        var response = controller.updateImageV1(1L, image, auth("mt@x.com", "MAINTAINER"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(productImageStorageService).delete("/v1/products/images/old.png");
    }

    @Test
    void updateImageV1_forbiddenForNonOwner() {
        Product existing = product(1L);
        existing.setCreatedBy("owner@x.com");
        when(productService.findById(1L)).thenReturn(existing);
        MockMultipartFile image = new MockMultipartFile("image", "p.png", "image/png", "bytes".getBytes());

        assertThatThrownBy(() -> controller.updateImageV1(1L, image, auth("other@x.com", "MAINTAINER")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(productImageStorageService, never()).store(any());
    }

    @Test
    void updateImageV1_missingProductReturns404() {
        when(productService.findById(9L)).thenReturn(null);
        MockMultipartFile image = new MockMultipartFile("image", "p.png", "image/png", "bytes".getBytes());

        assertThat(controller.updateImageV1(9L, image, auth("mt@x.com", "MAINTAINER")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteImageV1_adminClearsAndDeletesFile() {
        Product existing = product(1L);
        existing.setCreatedBy("owner@x.com");
        existing.setImageUrl("/v1/products/images/old.png");
        when(productService.findById(1L)).thenReturn(existing);
        Product cleared = product(1L);
        when(productService.updateImage(1L, null)).thenReturn(cleared);

        var response = controller.deleteImageV1(1L, auth("admin@x.com", "ADMIN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(productImageStorageService).delete("/v1/products/images/old.png");
    }

    @Test
    void findAllV1_returnsApproved() {
        when(productService.findAllApproved()).thenReturn(List.of(product(1L)));
        assertThat(controller.findAllV1()).hasSize(1);
    }

    @Test
    void findAllForAdminV1_returnsAll() {
        when(productService.findAll()).thenReturn(List.of(product(1L)));
        assertThat(controller.findAllForAdminV1()).hasSize(1);
    }

    @Test
    void findMineV1_usesAuthenticatedEmail() {
        when(productService.findByCreatedBy("mt@x.com")).thenReturn(List.of(product(1L)));
        assertThat(controller.findMineV1(auth("mt@x.com", "MAINTAINER"))).hasSize(1);
    }

    @Test
    void pendingQueues_queryByStatus() {
        when(productService.findByStatus(ProductStatus.PENDING_MANAGER)).thenReturn(List.of(product(1L)));
        when(productService.findByStatus(ProductStatus.PENDING_PRODUCT_SPECIALIST)).thenReturn(List.of());
        when(productService.findByStatus(ProductStatus.PENDING_SALESMAN)).thenReturn(List.of());
        when(productService.findByStatus(ProductStatus.PENDING_ADMIN)).thenReturn(List.of());

        assertThat(controller.pendingManagerV1()).hasSize(1);
        assertThat(controller.pendingSpecialistV1()).isEmpty();
        assertThat(controller.pendingSalesmanV1()).isEmpty();
        assertThat(controller.pendingAdminV1()).isEmpty();
    }

    @Test
    void resubmitV1_delegates() {
        when(productService.resubmit(eq(1L), any(Product.class), eq("mt@x.com"))).thenReturn(product(1L));
        assertThat(controller.resubmitV1(1L, product(1L), auth("mt@x.com", "MAINTAINER"))).isNotNull();
    }

    @Test
    void managerReviewV1_delegates() {
        when(productService.managerReview(eq(1L), eq("mg@x.com"), anyBoolean(), any())).thenReturn(product(1L));

        controller.managerReviewV1(1L, new ProductController.ReviewRequest(true, null), auth("mg@x.com", "MANAGER"));

        verify(productService).managerReview(1L, "mg@x.com", true, null);
    }

    @Test
    void specialistReviewV1_delegates() {
        when(productService.specialistReview(eq(1L), eq("sp@x.com"), anyBoolean(), any())).thenReturn(product(1L));

        controller.specialistReviewV1(1L, new ProductController.ReviewRequest(true, null),
                auth("sp@x.com", "PRODUCT_SPECIALIST"));

        verify(productService).specialistReview(1L, "sp@x.com", true, null);
    }

    @Test
    void salesmanReviewV1_delegates() {
        when(productService.salesmanReview(eq(1L), eq("sl@x.com"), anyBoolean(), any())).thenReturn(product(1L));

        controller.salesmanReviewV1(1L, new ProductController.ReviewRequest(false, "no"),
                auth("sl@x.com", "SALESMAN"));

        verify(productService).salesmanReview(1L, "sl@x.com", false, "no");
    }

    @Test
    void adminReviewV1_delegates() {
        when(productService.adminReview(eq(1L), eq("a@x.com"), anyBoolean(), any())).thenReturn(product(1L));

        controller.adminReviewV1(1L, new ProductController.ReviewRequest(false, "no"), auth("a@x.com", "ADMIN"));

        verify(productService).adminReview(1L, "a@x.com", false, "no");
    }

    @Test
    void notificationsV1_usesEmailAndRole() {
        ProductNotification n = ProductNotification.builder().id(1L).message("hi").build();
        when(productNotificationService.findByRecipient("a@x.com", "ADMIN")).thenReturn(List.of(n));

        assertThat(controller.notificationsV1(auth("a@x.com", "ADMIN"))).hasSize(1);
    }

    @Test
    void markNotificationReadV1_delegates() {
        controller.markNotificationReadV1(1L);
        verify(productNotificationService).markRead(1L);
    }

    @Test
    void searchV1_blankReturnsApproved() {
        when(productService.findAllApproved()).thenReturn(List.of(product(1L)));
        assertThat(controller.searchV1("  ")).hasSize(1);
    }

    @Test
    void updateQuantityV1_returnsOkOrNotFound() {
        when(productService.updateQuantity(1L, 2)).thenReturn(product(1L));
        when(productService.updateQuantity(9L, 2)).thenReturn(null);

        assertThat(controller.updateQuantityV1(1L, 2).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.updateQuantityV1(9L, 2).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void addQuantityV1_delegates() {
        when(productService.addQuantity(1L, 3)).thenReturn(product(1L));
        assertThat(controller.addQuantityV1(1L, 3).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updatePriceV1_delegates() {
        when(productService.updatePrice(eq(1L), any())).thenReturn(product(1L));
        assertThat(controller.updatePriceV1(1L, new BigDecimal("2.00")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateNameV1_delegates() {
        when(productService.updateName(1L, "New")).thenReturn(product(1L));
        assertThat(controller.updateNameV1(1L, "New").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void findByIdV1_delegates() {
        when(productService.findById(1L)).thenReturn(product(1L));
        assertThat(controller.findByIdV1(1L)).isNotNull();
    }

    @Test
    void currentRole_defaultsToUserWhenNoAuthorities() {
        Authentication anon = new UsernamePasswordAuthenticationToken("x", null, List.of());
        when(productNotificationService.findByRecipient(anyString(), eq("USER"))).thenReturn(List.of());

        controller.notificationsV1(anon);

        verify(productNotificationService).findByRecipient("x", "USER");
    }
}
