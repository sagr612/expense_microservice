package com.greatest.notification_service.services;

import com.greatest.notification_service.dtos.NotificationCreatedEvent;
import com.greatest.notification_service.dtos.NotificationDto;
import com.greatest.notification_service.entities.Notification;
import com.greatest.notification_service.entities.NotificationType;
import com.greatest.notification_service.exceptions.NotificationNotFoundException;
import com.greatest.notification_service.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final ApplicationEventPublisher eventPublisher;

    public Notification create(String userId, String title, String body, NotificationType type) {
        Objects.requireNonNull(userId, "userId must not be null");

        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .type(type)
                .isRead(false)
                .build();

        Notification saved = repository.save(notification);
        log.info("Notification created - id={}, userId={}, type={}", saved.getId(), userId, type);
        eventPublisher.publishEvent(new NotificationCreatedEvent(saved.getId(), userId, title, body, type));
        return saved;
    }

    public Page<NotificationDto> getNotifications(String userId, int page, int size) {
        Objects.requireNonNull(userId, "userId must not be null");
        int safeSize= Math.clamp(size, 1, 50);
        int safePage= Math.max(0, page);
        return repository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(safePage, safeSize))
                .map(this::toDto);
    }

    public Long unreadCount(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        return repository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markRead(String notificationId, String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found"));
        if (!notification.getUserId().equals(userId)) {
            log.warn("Access denied: userId={} attempted to mark notification {} belonging to another user", userId, notificationId);
            throw new AccessDeniedException("Not your notification");
        }
        notification.setIsRead(true);
        log.info("Notification marked as read - id={}, userId={}", notificationId, userId);
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