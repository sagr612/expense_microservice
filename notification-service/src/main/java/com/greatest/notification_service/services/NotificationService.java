package com.greatest.notification_service.services;

import com.greatest.notification_service.dtos.NotificationDto;
import com.greatest.notification_service.entities.Notification;
import com.greatest.notification_service.entities.NotificationType;
import com.greatest.notification_service.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;

    public Notification create(String userId, String title, String body, NotificationType type) {
        Objects.requireNonNull(userId, "userId must not be null");

        log.info("Expense created for  userId: {}", userId);
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .type(type)
                .isRead(false)
                .build();

        return repository.save(notification);
    }

    public List<NotificationDto> getNotifications(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        return repository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public Long unreadCount(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        return repository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markRead(String notificationId, String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!notification.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not your notification");
        }
        notification.setIsRead(true);
        repository.save(notification);
    }

    private NotificationDto toDto(Notification notification) {
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