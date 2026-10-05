package com.greatest.notification_service.services;

import com.greatest.notification_service.dtos.NotificationCreatedEvent;
import com.greatest.notification_service.entities.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PushNotificationDispatcherTest {

    @Mock
    private PushTokenService pushTokenService;

    @InjectMocks
    private PushNotificationDispatcher dispatcher;

    @Test
    void onNotificationCreated_delegatesToPushTokenService() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(
                "notif-1",
                "user-1",
                "Budget Alert",
                "80% of budget used",
                NotificationType.BUDGET_ALERT
        );

        dispatcher.onNotificationCreated(event);
        Map<String, Object> data = Map.of(
                "notificationId", "notif-1",
                "type", "BUDGET_ALERT"
        );
        verify(pushTokenService).sendToUser(
                "user-1",
                "Budget Alert",
                "80% of budget used",
                data
        );
    }
}
