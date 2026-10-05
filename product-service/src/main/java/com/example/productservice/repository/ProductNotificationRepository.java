package com.example.productservice.repository;

import com.example.productservice.entity.ProductNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductNotificationRepository extends JpaRepository<ProductNotification, Long> {

    List<ProductNotification> findByRecipientEmailOrRecipientRoleOrderByIdDesc(
            String recipientEmail, String recipientRole);
}