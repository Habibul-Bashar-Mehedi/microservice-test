package com.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductDocument;
import com.example.productservice.entity.ProductStatus;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.repository.ProductSearchRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private ProductSearchRepository searchRepository;
    @Mock
    private ElasticsearchOperations operations;
    @Mock
    private ProductRepository productRepository;

    private ProductSearchService service;

    @BeforeEach
    void setUp() {
        service = new ProductSearchService(searchRepository, operations, productRepository);
    }

    private Product product(Long id, ProductStatus status) {
        return Product.builder().id(id).name("Phone").price(new BigDecimal("10.00"))
                .availableQuantity(5).status(status).build();
    }

    @Test
    void index_approvedProductIsStored() {
        service.index(product(1L, ProductStatus.APPROVED));

        verify(searchRepository).save(any(ProductDocument.class));
    }

    @Test
    void index_nonApprovedProductIsRemoved() {
        service.index(product(2L, ProductStatus.PENDING_MANAGER));

        verify(searchRepository).deleteById(2L);
    }

    @Test
    void index_swallowsElasticsearchFailures() {
        when(searchRepository.save(any())).thenThrow(new RuntimeException("es down"));

        assertThatCode(() -> service.index(product(1L, ProductStatus.APPROVED)))
                .doesNotThrowAnyException();
    }

    @Test
    void delete_swallowsFailures() {
        assertThatCode(() -> service.delete(1L)).doesNotThrowAnyException();
        verify(searchRepository).deleteById(1L);
    }

    @Test
    void run_reindexesOnlyApprovedProducts() {
        Product approved = product(1L, ProductStatus.APPROVED);
        Product pending = product(2L, ProductStatus.PENDING_MANAGER);
        when(productRepository.findAll()).thenReturn(List.of(approved, pending));

        service.run(null);

        verify(searchRepository).deleteAll();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ProductDocument>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(searchRepository).saveAll(captor.capture());
        var saved = new java.util.ArrayList<ProductDocument>();
        captor.getValue().forEach(saved::add);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getId()).isEqualTo(1L);
    }
}