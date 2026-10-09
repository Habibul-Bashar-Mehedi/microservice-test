package com.example.productservice.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stores product images on the local filesystem and exposes a non-sensitive public URL.
 * Actual file content is validated (not just the declared content type / filename) so a
 * renamed executable or unsupported format is rejected.
 */
@Slf4j
@Service
public class ProductImageStorageService {

    /** URL prefix (below the service root) under which stored files are served. */
    public static final String PUBLIC_URL_PREFIX = "/v1/products/images/";

    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "png", "gif");

    private final Path storageDir;
    private final long maxSizeBytes;

    public ProductImageStorageService(
            @Value("${product.image.storage-dir:./data/product-images}") String storageDir,
            @Value("${product.image.max-size-bytes:5242880}") long maxSizeBytes) {
        this.storageDir = Paths.get(storageDir).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeBytes;
    }

    /**
     * Validates and persists an uploaded image, returning the public URL path to store on the
     * product. Returns {@code null} for an absent/empty upload.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Image is too large. Maximum allowed size is " + (maxSizeBytes / (1024 * 1024)) + " MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded image");
        }

        String format = detectFormat(bytes);
        if (format == null || !ALLOWED_FORMATS.contains(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported image format. Allowed formats are PNG, JPEG and GIF");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extensionFor(format);
        try {
            Files.createDirectories(storageDir);
            Files.write(storageDir.resolve(filename), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            log.warn("Failed to store product image {}: {}", filename, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store the image");
        }
        return PUBLIC_URL_PREFIX + filename;
    }

    /**
     * Persists a generated image under a deterministic, sanitized name. If a file with that name
     * already exists it is reused, making repeated runs idempotent.
     */
    public String storeGeneratedPng(byte[] pngBytes, String key) {
        String safeKey = key == null ? "default" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (safeKey.isBlank()) {
            safeKey = "default";
        }
        String filename = safeKey + ".png";
        Path target = storageDir.resolve(filename).normalize();
        if (!target.startsWith(storageDir)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid generated image name");
        }
        try {
            Files.createDirectories(storageDir);
            if (!Files.exists(target)) {
                Files.write(target, pngBytes, StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            log.warn("Failed to store generated image {}: {}", filename, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store generated image");
        }
        return PUBLIC_URL_PREFIX + filename;
    }

    /**
     * Stores a bundled seed image under its given file name if it is not already present.
     * Used to restore images referenced by seeded products on a fresh installation/volume.
     */
    public void storeIfAbsent(String filename, byte[] bytes) {
        if (filename == null || filename.isBlank() || bytes == null || bytes.length == 0) {
            return;
        }
        String safeName = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "");
        if (safeName.isBlank()) {
            return;
        }
        Path target = storageDir.resolve(safeName).normalize();
        if (!target.startsWith(storageDir)) {
            return;
        }
        try {
            Files.createDirectories(storageDir);
            if (!Files.exists(target)) {
                Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            log.warn("Failed to store seed image {}: {}", safeName, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store seed image");
        }
    }

    /** Deletes a previously stored image, ignoring blank/unknown references. */
    public void delete(String imageUrl) {
        Path target = resolveSafely(imageUrl);
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete product image {}: {}", imageUrl, e.getMessage());
        }
    }

    /** Resolves a stored URL back to a file inside the storage directory, or {@code null}. */
    private Path resolveSafely(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        if (filename.isBlank()) {
            return null;
        }
        Path target = storageDir.resolve(filename).normalize();
        if (!target.startsWith(storageDir)) {
            log.warn("Rejected suspicious image reference: {}", imageUrl);
            return null;
        }
        return target;
    }

    /** Reads the actual image format from the byte content using ImageIO readers. */
    private String detectFormat(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                return reader.getFormatName().toLowerCase(Locale.ROOT);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private String extensionFor(String format) {
        return "jpeg".equals(format) ? "jpg" : format;
    }
}
