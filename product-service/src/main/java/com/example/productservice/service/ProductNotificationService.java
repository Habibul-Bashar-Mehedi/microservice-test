package com.example.productservice.service;

import com.example.productservice.entity.ProductNotification;
import com.example.productservice.repository.ProductNotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductNotificationService {

    private final ProductNotificationRepository notificationRepository;

    public void notify(String recipientEmail, String recipientRole, Long productId, String productName,
            String message) {
        notificationRepository.save(ProductNotification.builder()
                .recipientEmail(recipientEmail)
                .recipientRole(recipientRole)
                .productId(productId)
                .productName(productName)
                .message(message)
                .createdAt(LocalDateTime.now())
                .read(false)
                .build());
    }

    public List<ProductNotification> findByRecipient(String email, String role) {
        return notificationRepository.findByRecipientEmailOrRecipientRoleOrderByIdDesc(email, role);
    }

    public void markRead(Long id) {
        notificationRepository.findById(id).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
    }
}