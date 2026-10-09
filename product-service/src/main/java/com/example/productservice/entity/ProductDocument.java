package com.example.productservice.entity;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Integer)
    private Integer availableQuantity;

    @Field(type = FieldType.Keyword)
    private String imageUrl;

    public static ProductDocument from(Product product) {
        return ProductDocument.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .availableQuantity(product.getAvailableQuantity())
                .imageUrl(product.getImageUrl())
                .build();
    }
}