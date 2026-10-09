package com.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ProductImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private ProductImageStorageService service;

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    @BeforeEach
    void setUp() {
        service = new ProductImageStorageService(tempDir.toString(), MAX_BYTES);
    }

    private byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    void store_png_returnsUrlAndPersistsFile() throws Exception {
        byte[] bytes = imageBytes("png");
        MockMultipartFile file = new MockMultipartFile("image", "photo.png", "image/png", bytes);

        String url = service.store(file);

        assertThat(url).startsWith(ProductImageStorageService.PUBLIC_URL_PREFIX).endsWith(".png");
        Path stored = tempDir.resolve(url.substring(url.lastIndexOf('/') + 1));
        assertThat(stored).exists();
        assertThat(Files.readAllBytes(stored)).isEqualTo(bytes);
    }

    @Test
    void store_jpeg_usesJpgExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "photo.jpeg", "image/jpeg", imageBytes("jpeg"));

        assertThat(service.store(file)).endsWith(".jpg");
    }

    @Test
    void store_gif_supported() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "photo.gif", "image/gif", imageBytes("gif"));

        assertThat(service.store(file)).endsWith(".gif");
    }

    @Test
    void store_rejectsDisguisedNonImageContent() {
        MockMultipartFile file = new MockMultipartFile("image", "fake.png", "image/png",
                "not really an image".getBytes());

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void store_rejectsUnsupportedFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "photo.bmp", "image/bmp", imageBytes("bmp"));

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void store_rejectsOversizedFile() throws Exception {
        ProductImageStorageService small = new ProductImageStorageService(tempDir.toString(), 4);
        MockMultipartFile file = new MockMultipartFile("image", "photo.png", "image/png", imageBytes("png"));

        assertThatThrownBy(() -> small.store(file))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void store_emptyOrNullReturnsNull() {
        assertThat(service.store(null)).isNull();
        assertThat(service.store(new MockMultipartFile("image", "empty.png", "image/png", new byte[0]))).isNull();
    }

    @Test
    void delete_removesStoredFile() throws Exception {
        String url = service.store(new MockMultipartFile("image", "photo.png", "image/png", imageBytes("png")));
        Path stored = tempDir.resolve(url.substring(url.lastIndexOf('/') + 1));
        assertThat(stored).exists();

        service.delete(url);

        assertThat(stored).doesNotExist();
    }

    @Test
    void delete_ignoresPathTraversalReference() throws Exception {
        Path outside = tempDir.getParent().resolve("secret-" + System.nanoTime() + ".txt");
        Files.writeString(outside, "secret");
        try {
            service.delete("/v1/products/images/../../" + outside.getFileName());

            assertThat(outside).exists();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void delete_blankOrMissingIsNoOp() {
        service.delete(null);
        service.delete("  ");
        service.delete("/v1/products/images/does-not-exist.png");
    }

    @Test
    void storeIfAbsent_writesFileOnce() throws Exception {
        byte[] bytes = imageBytes("png");

        service.storeIfAbsent("seed-abc.png", bytes);
        service.storeIfAbsent("seed-abc.png", "different".getBytes());

        Path stored = tempDir.resolve("seed-abc.png");
        assertThat(stored).exists();
        assertThat(Files.readAllBytes(stored)).isEqualTo(bytes);
    }

    @Test
    void storeIfAbsent_ignoresUnsafeNamesAndEmptyBytes() {
        service.storeIfAbsent("../../etc/passwd", new byte[] {1, 2, 3});
        service.storeIfAbsent("ok.png", new byte[0]);
        service.storeIfAbsent(null, new byte[] {1});

        assertThat(tempDir.resolve("etc").resolve("passwd")).doesNotExist();
    }

    @Test
    void storeGeneratedPng_writesFileAndReturnsUrl() throws Exception {
        byte[] bytes = imageBytes("png");

        String url = service.storeGeneratedPng(bytes, "category-OTHER");

        assertThat(url).isEqualTo("/v1/products/images/category-other.png");
        Path stored = tempDir.resolve("category-other.png");
        assertThat(stored).exists();
        assertThat(Files.readAllBytes(stored)).isEqualTo(bytes);
    }

    @Test
    void storeGeneratedPng_isIdempotentAndKeepsExistingFile() throws Exception {
        service.storeGeneratedPng(imageBytes("png"), "category-CHAL");
        Path stored = tempDir.resolve("category-chal.png");
        byte[] original = Files.readAllBytes(stored);

        service.storeGeneratedPng("different".getBytes(), "category-CHAL");

        assertThat(Files.readAllBytes(stored)).isEqualTo(original);
    }

    @Test
    void storeGeneratedPng_sanitizesUnsafeKey() throws Exception {
        String url = service.storeGeneratedPng(imageBytes("png"), "../evil name!");

        assertThat(url).startsWith("/v1/products/images/").doesNotContain("..").doesNotContain(" ");
    }
}
