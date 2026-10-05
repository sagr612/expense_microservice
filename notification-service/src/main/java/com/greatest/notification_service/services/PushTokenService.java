package com.greatest.notification_service.services;

import com.greatest.notification_service.entities.PushToken;
import com.greatest.notification_service.repositories.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

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
        pushTokenRepository
                .findByUserId(userId)
                .ifPresentOrElse(
                        token->{
                            if(!expoToken.equals(token.getExpoToken())) {
                                pushTokenRepository.deleteByExpoToken(expoToken);
                                pushTokenRepository.flush();
                                token.setExpoToken(expoToken);
                            }
                            pushTokenRepository.save(token);
                        },
                        ()->{
                            pushTokenRepository.deleteByExpoToken(expoToken);
                            pushTokenRepository.flush();
                            pushTokenRepository.save(PushToken.builder()
                                            .userId(userId)
                                    .expoToken(expoToken)
                                    .build());

                        }
                );

        log.info("Push token saved for userId: {}", userId);
    }

    public void sendToUser(String userId, String title, String body, Map<String, Object> data) {
        if(userId==null || title==null || body==null) {
            log.error("Error sending push token");
            return;
        }
        pushTokenRepository.findByUserId(userId)
                .ifPresentOrElse(
                        token -> expoPushService.sendPushNotification(
                                token.getExpoToken(), title, body,data),
                        () -> log.warn(
                                "No push token found for userId: {}", userId)
                );
    }



    @Transactional
    public void deleteToken(String userId) {
        if(userId!=null) {
            pushTokenRepository.deleteByUserId(userId);
            log.info("Deleted push token for userId: {}", userId);
        }
    }

}