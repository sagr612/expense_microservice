package com.greatest.notification_service.repositories;

import com.greatest.notification_service.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Long countByUserIdAndIsReadFalse(String userId);

    @Modifying
    @Query("""
            DELETE FROM Notification n WHERE n.userId = :userId
            """)
    void deleteByUserId(@Param("userId") String userId);
}