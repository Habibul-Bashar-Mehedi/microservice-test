package com.example.productservice.service;

import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductDocument;
import com.example.productservice.entity.ProductStatus;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.repository.ProductSearchRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService implements ApplicationRunner {

    private final ProductSearchRepository searchRepository;
    private final ElasticsearchOperations operations;
    private final ProductRepository productRepository;

    public List<ProductDocument> search(String query) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q.matchPhrasePrefix(m -> m.field("name").query(query)))
                .build();
        return operations.search(nativeQuery, ProductDocument.class)
                .stream()
                .map(SearchHit::getContent)
                .toList();
    }

    public void index(Product product) {
        try {
            if (product.getStatus() == ProductStatus.APPROVED) {
                searchRepository.save(ProductDocument.from(product));
            } else {
                searchRepository.deleteById(product.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to index product {} in Elasticsearch: {}", product.getId(), e.getMessage());
        }
    }

    public void delete(Long id) {
        try {
            searchRepository.deleteById(id);
        } catch (Exception e) {
            log.warn("Failed to delete product {} from Elasticsearch: {}", id, e.getMessage());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Product> products = productRepository.findAll().stream()
                    .filter(product -> product.getStatus() == ProductStatus.APPROVED)
                    .toList();
            searchRepository.deleteAll();
            searchRepository.saveAll(products.stream().map(ProductDocument::from).toList());
            log.info("Reindexed {} approved products into Elasticsearch", products.size());
        } catch (Exception e) {
            log.warn("Elasticsearch reindex failed: {}", e.getMessage());
        }
    }
}