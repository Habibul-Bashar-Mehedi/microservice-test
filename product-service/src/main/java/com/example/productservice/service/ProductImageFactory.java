package com.example.productservice.service;

import com.example.productservice.entity.Category;
import com.example.productservice.entity.Product;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Renders a simple, real PNG image for a product, using its name and category. */
@Component
public class ProductImageFactory {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final int MAX_TEXT_WIDTH = WIDTH - 96;

    private static final Map<Category, Color> COLORS = Map.of(
            Category.CHAL, new Color(0xC2, 0x8A, 0x3C),
            Category.DAL, new Color(0xD9, 0x7A, 0x1F),
            Category.ATA, new Color(0xB8, 0x86, 0x5A),
            Category.MOYDA, new Color(0xC9, 0xA2, 0x4B),
            Category.CHINI, new Color(0x3E, 0x8E, 0xC9),
            Category.MOSHLA, new Color(0xB5, 0x3A, 0x2E),
            Category.OTHER, new Color(0x43, 0x38, 0xCA));

    public byte[] render(Product product) {
        String name = (product == null || product.getName() == null || product.getName().isBlank())
                ? "Product"
                : product.getName().trim();
        Category category = (product == null || product.getCategory() == null)
                ? Category.OTHER
                : product.getCategory();

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color background = COLORS.getOrDefault(category, COLORS.get(Category.OTHER));
            graphics.setColor(background);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            graphics.setColor(new Color(255, 255, 255, 26));
            graphics.fillOval(-140, -170, 520, 520);
            graphics.fillOval(WIDTH - 250, HEIGHT - 220, 460, 460);

            int fontSize = name.length() > 44 ? 34 : name.length() > 28 ? 42 : name.length() > 16 ? 52 : 62;
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            FontMetrics metrics = graphics.getFontMetrics();
            List<String> lines = wrap(name, metrics, MAX_TEXT_WIDTH, 3);

            int lineHeight = metrics.getHeight();
            int baseline = HEIGHT / 2 - (lineHeight * lines.size()) / 2 + metrics.getAscent() / 2 - 8;
            graphics.setColor(Color.WHITE);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                graphics.drawString(line, (WIDTH - metrics.stringWidth(line)) / 2, baseline + i * lineHeight);
            }

            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
            FontMetrics badgeMetrics = graphics.getFontMetrics();
            String badge = category.name();
            graphics.setColor(new Color(255, 255, 255, 205));
            graphics.drawString(badge, (WIDTH - badgeMetrics.stringWidth(badge)) / 2, HEIGHT - 40);
        } finally {
            graphics.dispose();
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to render product image");
        }
    }

    private List<String> wrap(String text, FontMetrics metrics, int maxWidth, int maxLines) {
        String[] words = text.split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (metrics.stringWidth(candidate) <= maxWidth || current.length() == 0) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
                if (lines.size() == maxLines) {
                    current.setLength(0);
                    break;
                }
            }
        }
        if (current.length() > 0 && lines.size() < maxLines) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add(text);
        }
        return lines;
    }
}
