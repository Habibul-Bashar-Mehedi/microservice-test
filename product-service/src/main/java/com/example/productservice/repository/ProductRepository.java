package com.example.productservice.repository;

import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByName(String name);

    List<Product> findAllByOrderByIdDesc();

    List<Product> findByStatusOrderByIdDesc(ProductStatus status);

    List<Product> findByCreatedByOrderByIdDesc(String createdBy);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}