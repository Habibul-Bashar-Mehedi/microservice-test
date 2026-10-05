package com.example.productservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductNotification;
import com.example.productservice.entity.ProductStatus;
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

    private ProductController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductController(productService, productNotificationService, productSearchService);
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
                .availableQuantity(5).status(ProductStatus.PENDING_MAINTAINER).build();
    }

    @Test
    void createV1_returns201AndUsesAuthenticatedEmail() {
        when(productService.create(any(Product.class), eq("m@x.com"))).thenReturn(product(1L));

        var response = controller.createV1(product(null), auth("m@x.com", "MANAGER"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
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
        when(productService.findByCreatedBy("m@x.com")).thenReturn(List.of(product(1L)));
        assertThat(controller.findMineV1(auth("m@x.com", "MANAGER"))).hasSize(1);
    }

    @Test
    void pendingQueues_queryByStatus() {
        when(productService.findByStatus(ProductStatus.PENDING_MAINTAINER)).thenReturn(List.of(product(1L)));
        when(productService.findByStatus(ProductStatus.PENDING_ADMIN)).thenReturn(List.of());

        assertThat(controller.pendingMaintainerV1()).hasSize(1);
        assertThat(controller.pendingAdminV1()).isEmpty();
    }

    @Test
    void resubmitV1_delegates() {
        when(productService.resubmit(eq(1L), any(Product.class), eq("m@x.com"))).thenReturn(product(1L));
        assertThat(controller.resubmitV1(1L, product(1L), auth("m@x.com", "MANAGER"))).isNotNull();
    }

    @Test
    void maintainerReviewV1_delegates() {
        when(productService.maintainerReview(eq(1L), eq("mt@x.com"), anyBoolean(), any())).thenReturn(product(1L));

        controller.maintainerReviewV1(1L, new ProductController.ReviewRequest(true, null), auth("mt@x.com", "MAINTAINER"));

        verify(productService).maintainerReview(1L, "mt@x.com", true, null);
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