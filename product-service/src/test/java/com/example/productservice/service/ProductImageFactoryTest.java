package com.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.productservice.entity.Category;
import com.example.productservice.entity.Product;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProductImageFactoryTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private final ProductImageFactory factory = new ProductImageFactory();

    private Product product(String name, Category category) {
        return Product.builder().name(name).category(category).price(BigDecimal.ONE).availableQuantity(1).build();
    }

    @Test
    void render_producesValidPngForEveryCategory() throws Exception {
        for (Category category : Category.values()) {
            byte[] bytes = factory.render(product("Sample Item " + category, category));

            assertThat(bytes).isNotEmpty();
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            assertThat(image).as("decodable image for %s", category).isNotNull();
            assertThat(image.getWidth()).isEqualTo(640);
            assertThat(image.getHeight()).isEqualTo(480);
        }
    }

    @Test
    void render_handlesLongNamesAndNulls() throws Exception {
        byte[] longName = factory.render(product("Approval Product 1791199128 with an extremely long name here", Category.OTHER));
        byte[] nullName = factory.render(Product.builder().category(Category.CHAL).price(BigDecimal.ONE)
                .availableQuantity(1).build());

        assertThat(ImageIO.read(new ByteArrayInputStream(longName))).isNotNull();
        assertThat(ImageIO.read(new ByteArrayInputStream(nullName))).isNotNull();
    }
}
