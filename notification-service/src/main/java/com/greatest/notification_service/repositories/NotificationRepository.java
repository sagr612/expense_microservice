package com.greatest.notification_service.repositories;

import com.greatest.notification_service.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository
        extends JpaRepository<Notification, String> {

    List<Notification>
    findByUserIdOrderByCreatedAtDesc(
            String userId
    );

    Long countByUserIdAndIsReadFalse(
            String userId
    );

    void deleteByUserId(String userId);
}