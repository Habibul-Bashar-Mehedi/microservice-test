package com.example.productservice.service;

import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Copies the product images bundled with the repository into the configured storage directory
 * on startup, so a fresh clone (or a fresh Docker volume) can serve the images referenced by the
 * seeded products. Existing files are never overwritten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedImageLoader implements ApplicationRunner {

    private final ProductImageStorageService imageStorageService;

    @Override
    public void run(ApplicationArguments args) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:seed-product-images/*");
        } catch (Exception e) {
            log.warn("Could not scan seed product images: {}", e.getMessage());
            return;
        }

        int copied = 0;
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }
            try (InputStream input = resource.getInputStream()) {
                imageStorageService.storeIfAbsent(filename, input.readAllBytes());
                copied++;
            } catch (Exception e) {
                log.warn("Skipping seed image {}: {}", filename, e.getMessage());
            }
        }
        if (copied > 0) {
            log.info("Ensured {} seed product image(s) are available", copied);
        }
    }
}
