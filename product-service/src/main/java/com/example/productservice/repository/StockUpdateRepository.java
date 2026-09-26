package com.example.productservice.repository;

import com.example.productservice.entity.StockUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockUpdateRepository extends JpaRepository<StockUpdate, Long> {
}