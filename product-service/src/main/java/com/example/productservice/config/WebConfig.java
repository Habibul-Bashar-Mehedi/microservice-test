package com.example.productservice.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves uploaded product images from the configured storage directory. Only files inside that
 * directory are exposed, and responses contain no filesystem paths.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String storageDir;

    public WebConfig(@Value("${product.image.storage-dir:./data/product-images}") String storageDir) {
        this.storageDir = storageDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Paths.get(storageDir).toAbsolutePath().normalize();
        String location = dir.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/v1/products/images/**")
                .addResourceLocations(location);
    }
}
