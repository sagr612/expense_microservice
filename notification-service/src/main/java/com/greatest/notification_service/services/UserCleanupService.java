package com.greatest.notification_service.services;

import com.greatest.notification_service.repositories.NotificationRepository;
import com.greatest.notification_service.repositories.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCleanupService {

    private final PushTokenRepository pushTokenRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public void deleteUserData(String userId) {

        log.info(
                "Deleting data for user {}",
                userId
        );

        pushTokenRepository.deleteByUserId(userId);

        notificationRepository.deleteByUserId(userId);

        log.info(
                "Successfully deleted all data for user {}",
                userId
        );
    }
}