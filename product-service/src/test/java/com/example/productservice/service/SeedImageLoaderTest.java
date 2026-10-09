package com.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeedImageLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void copiesBundledSeedImagesIntoStorage() throws Exception {
        ProductImageStorageService storage = new ProductImageStorageService(tempDir.toString(), 5 * 1024 * 1024);
        SeedImageLoader loader = new SeedImageLoader(storage);

        loader.run(null);

        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files.filter(Files::isRegularFile).count()).isGreaterThanOrEqualTo(46);
        }
    }

    @Test
    void doesNotOverwriteExistingFiles() throws Exception {
        Path existing = tempDir.resolve("existing.jpg");
        Files.writeString(existing, "original");
        ProductImageStorageService storage = new ProductImageStorageService(tempDir.toString(), 5 * 1024 * 1024);

        new SeedImageLoader(storage).run(null);

        assertThat(Files.readString(existing)).isEqualTo("original");
    }
}
