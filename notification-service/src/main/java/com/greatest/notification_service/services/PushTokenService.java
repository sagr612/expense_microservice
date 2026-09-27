package com.greatest.notification_service.services;

import com.greatest.notification_service.entities.PushToken;
import com.greatest.notification_service.repositories.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;
    private final ExpoPushService expoPushService;

    @Transactional
    public void saveToken(String userId, String expoToken) {
        if(userId == null || expoToken == null) {
            log.error("Error saving push token");
            return;
        }
        PushToken token = pushTokenRepository
                .findByUserId(userId)
                .orElse(PushToken.builder().userId(userId).build());
        token.setExpoToken(expoToken);
        pushTokenRepository.deleteByExpoToken(expoToken);
        pushTokenRepository.save(token);
        log.info("Push token saved for userId: {}", userId);
    }

    public void sendToUser(String userId, String title, String body) {
        if(userId==null || title==null || body==null) {
            log.error("Error sending push token");
            return;
        }
        pushTokenRepository.findByUserId(userId)
                .ifPresentOrElse(
                        token -> expoPushService.sendPushNotification(
                                token.getExpoToken(), title, body),
                        () -> log.warn(
                                "No push token found for userId: {}", userId)
                );
    }
}