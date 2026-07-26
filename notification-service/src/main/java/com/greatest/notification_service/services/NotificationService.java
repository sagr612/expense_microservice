package com.greatest.notification_service.services;

import com.greatest.notification_service.dtos.NotificationDto;
import com.greatest.notification_service.entities.Notification;
import com.greatest.notification_service.entities.NotificationType;
import com.greatest.notification_service.repositories.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;

    public Notification create(
            String userId,
            String title,
            String body,
            NotificationType type
    ) {
        log.info("Expense created for ff userId: {}", userId);

        Notification notification =
                Notification.builder()
                        .userId(userId)
                        .title(title)
                        .body(body)
                        .type(type)
                        .isRead(false)
                        .build();

        return repository.save(notification);
    }

    public List<NotificationDto> getNotifications(
            String userId
    ) {

        return repository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public Long unreadCount(
            String userId
    ) {

        return repository.countByUserIdAndIsReadFalse(
                userId
        );
    }

    @Transactional
    public void markRead(
            String notificationId
    ) {

        Notification notification =
                repository.findById(notificationId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Notification not found"
                                )
                        );

        notification.setIsRead(true);
    }

    private NotificationDto toDto(
            Notification notification
    ) {

        return NotificationDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .body(notification.getBody())
                .type(notification.getType().name())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}