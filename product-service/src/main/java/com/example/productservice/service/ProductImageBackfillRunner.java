package com.example.productservice.service;

import com.example.productservice.entity.Product;
import com.example.productservice.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On startup, gives every existing product a name-based image. Products that already have a
 * product-specific image (either user-uploaded or previously generated) are left untouched, so
 * repeated runs are idempotent. Shared category images from earlier backfills are upgraded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductImageBackfillRunner implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final ProductImageStorageService imageStorageService;
    private final ProductSearchService productSearchService;
    private final ProductImageFactory productImageFactory;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Product> needingImage = productRepository.findAll().stream()
                    .filter(this::needsNameBasedImage)
                    .toList();
            if (needingImage.isEmpty()) {
                return;
            }

            int updated = 0;
            for (Product product : needingImage) {
                String key = "product-" + (product.getId() != null ? product.getId() : "new");
                String imageUrl = imageStorageService.storeGeneratedPng(productImageFactory.render(product), key);
                product.setImageUrl(imageUrl);
                Product saved = productRepository.save(product);
                productSearchService.index(saved);
                updated++;
            }
            log.info("Backfilled name-based images for {} product(s)", updated);
        } catch (Exception e) {
            log.warn("Product image backfill skipped due to error: {}", e.getMessage());
        }
    }

    private boolean needsNameBasedImage(Product product) {
        String imageUrl = product.getImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            return true;
        }
        // Upgrade the shared category images produced by the earlier backfill.
        return imageUrl.contains("/category-");
    }
}
