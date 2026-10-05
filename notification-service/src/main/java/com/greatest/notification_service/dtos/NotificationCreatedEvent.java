package com.greatest.notification_service.dtos;

import com.greatest.notification_service.entities.Notification;
import com.greatest.notification_service.entities.NotificationType;

public record NotificationCreatedEvent(
        String notificationId,
        String userId,
        String title,
        String body,
        NotificationType type
) {}
