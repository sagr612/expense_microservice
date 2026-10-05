package com.greatest.notification_service.repositories;

import com.greatest.notification_service.entities.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, String> {
    Optional<PushToken> findByUserId(String userId);

    @Modifying
    @Query("""
            DELETE FROM PushToken p WHERE p.userId = :userId
            """)
    void deleteByUserId(@Param("userId") String userId);

    @Transactional
    @Modifying
    @Query("""
            DELETE FROM PushToken p WHERE p.expoToken = :expoToken
            """)
    void deleteByExpoToken(@Param("expoToken") String expoToken);
}