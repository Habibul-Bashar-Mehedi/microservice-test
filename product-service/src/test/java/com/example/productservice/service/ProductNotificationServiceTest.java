package com.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.productservice.entity.ProductNotification;
import com.example.productservice.repository.ProductNotificationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductNotificationServiceTest {

    @Mock
    private ProductNotificationRepository notificationRepository;

    private ProductNotificationService service;

    @BeforeEach
    void setUp() {
        service = new ProductNotificationService(notificationRepository);
    }

    @Test
    void notify_persistsUnreadNotificationWithTimestamp() {
        service.notify("manager@example.com", "MANAGER", 7L, "Phone", "please review");

        ArgumentCaptor<ProductNotification> captor = ArgumentCaptor.forClass(ProductNotification.class);
        verify(notificationRepository).save(captor.capture());
        ProductNotification saved = captor.getValue();
        assertThat(saved.getRecipientEmail()).isEqualTo("manager@example.com");
        assertThat(saved.getRecipientRole()).isEqualTo("MANAGER");
        assertThat(saved.getProductId()).isEqualTo(7L);
        assertThat(saved.getProductName()).isEqualTo("Phone");
        assertThat(saved.getMessage()).isEqualTo("please review");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getRead()).isFalse();
    }

    @Test
    void findByRecipient_delegatesToRepository() {
        when(notificationRepository.findByRecipientEmailOrRecipientRoleOrderByIdDesc("a@x.com", "USER"))
                .thenReturn(List.of(ProductNotification.builder().id(1L).build()));

        assertThat(service.findByRecipient("a@x.com", "USER")).hasSize(1);
    }

    @Test
    void markRead_existingNotificationIsMarkedReadAndSaved() {
        ProductNotification notification = ProductNotification.builder().id(1L).read(false).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        service.markRead(1L);

        assertThat(notification.getRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_missingNotificationDoesNothing() {
        when(notificationRepository.findById(9L)).thenReturn(Optional.empty());

        service.markRead(9L);

        verify(notificationRepository, never()).save(any());
    }
}
