package com.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.productservice.entity.Category;
import com.example.productservice.entity.Product;
import com.example.productservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductImageBackfillRunnerTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageStorageService imageStorageService;
    @Mock
    private ProductSearchService productSearchService;
    @Mock
    private ProductImageFactory productImageFactory;

    private ProductImageBackfillRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ProductImageBackfillRunner(productRepository, imageStorageService,
                productSearchService, productImageFactory);
    }

    private Product product(Long id, String imageUrl) {
        return Product.builder().id(id).name("Product " + id).price(BigDecimal.ONE)
                .availableQuantity(1).category(Category.OTHER).imageUrl(imageUrl).build();
    }

    @Test
    void backfill_givesNameBasedImageToProductsWithoutOne() {
        Product noImage = product(1L, null);
        Product blankImage = product(2L, "  ");
        Product uploaded = product(3L, "/v1/products/images/abc123.png");
        Product generated = product(4L, "/v1/products/images/product-4.png");
        when(productRepository.findAll()).thenReturn(List.of(noImage, blankImage, uploaded, generated));

        byte[] bytes1 = "one".getBytes();
        byte[] bytes2 = "two".getBytes();
        when(productImageFactory.render(noImage)).thenReturn(bytes1);
        when(productImageFactory.render(blankImage)).thenReturn(bytes2);
        when(imageStorageService.storeGeneratedPng(bytes1, "product-1"))
                .thenReturn("/v1/products/images/product-1.png");
        when(imageStorageService.storeGeneratedPng(bytes2, "product-2"))
                .thenReturn("/v1/products/images/product-2.png");
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        assertThat(noImage.getImageUrl()).isEqualTo("/v1/products/images/product-1.png");
        assertThat(blankImage.getImageUrl()).isEqualTo("/v1/products/images/product-2.png");
        assertThat(uploaded.getImageUrl()).isEqualTo("/v1/products/images/abc123.png");
        assertThat(generated.getImageUrl()).isEqualTo("/v1/products/images/product-4.png");

        verify(productImageFactory, never()).render(uploaded);
        verify(productImageFactory, never()).render(generated);
        verify(productSearchService, times(2)).index(any(Product.class));
    }

    @Test
    void backfill_upgradesSharedCategoryImages() {
        Product categoryImage = product(5L, "/v1/products/images/category-other.png");
        when(productRepository.findAll()).thenReturn(List.of(categoryImage));
        byte[] bytes = "five".getBytes();
        when(productImageFactory.render(categoryImage)).thenReturn(bytes);
        when(imageStorageService.storeGeneratedPng(bytes, "product-5"))
                .thenReturn("/v1/products/images/product-5.png");
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        assertThat(categoryImage.getImageUrl()).isEqualTo("/v1/products/images/product-5.png");
    }

    @Test
    void backfill_doesNothingWhenEveryProductAlreadyHasProductImage() {
        when(productRepository.findAll()).thenReturn(List.of(product(1L, "/v1/products/images/product-1.png")));

        runner.run(null);

        verifyNoInteractions(imageStorageService, productImageFactory, productSearchService);
    }

    @Test
    void backfill_swallowsErrorsSoStartupIsNotBroken() {
        when(productRepository.findAll()).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        verify(imageStorageService, never()).storeGeneratedPng(any(), anyString());
        verify(productRepository, never()).save(any());
    }
}
