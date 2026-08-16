package com.greatest.notification_service.repositories;

import com.greatest.notification_service.entities.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, String> {
    Optional<PushToken> findByUserId(String userId);

    void deleteByUserId(String userId);

    void deleteByExpoToken(String expoToken);
}