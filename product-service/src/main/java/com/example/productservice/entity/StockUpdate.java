package com.example.productservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stock_updates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdate {

    @Id
    private Long orderId;
}