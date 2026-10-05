package com.greatest.notification_service.services;

import com.greatest.notification_service.dtos.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class PushNotificationDispatcher {
    private final PushTokenService pushTokenService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        log.info("Transaction committed successfully, dispatching push notification to userId={}", event.userId());

        Map<String, Object> data = Map.of(
                "notificationId", event.notificationId() != null ? event.notificationId() : "",
                "type", event.type() != null ? event.type().name() : ""
        );


        pushTokenService.sendToUser(event.userId(), event.title(), event.body(), data);

    }
}
