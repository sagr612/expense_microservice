package com.greatest.notification_service.services;

import com.greatest.notification_service.repositories.NotificationRepository;
import com.greatest.notification_service.repositories.PushTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCleanupServiceTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private UserCleanupService userCleanupService;

    @Test
    void deleteUserData_deletesPushTokensAndNotifications() {
        userCleanupService.deleteUserData("user-1");

        verify(pushTokenRepository).deleteByUserId("user-1");
        verify(notificationRepository).deleteByUserId("user-1");
    }

    @Test
    void deleteUserData_deletesPushTokensBeforeNotifications() {
        // establishes call order per the method body: pushTokenRepository first, then notificationRepository
        userCleanupService.deleteUserData("user-1");

        var inOrder = inOrder(pushTokenRepository, notificationRepository);
        inOrder.verify(pushTokenRepository).deleteByUserId("user-1");
        inOrder.verify(notificationRepository).deleteByUserId("user-1");
    }

    @Test
    void propagatesException_whenPushTokenDeletionFails() {
        doThrow(new RuntimeException("Database Error")).when(pushTokenRepository).deleteByUserId("user-1");

        assertThrows(RuntimeException.class, () -> userCleanupService.deleteUserData("user-1"));

        verify(notificationRepository, never()).deleteByUserId(anyString());
    }

    @Test
    void propagatesException_whenNotificationDeletionFails() {
        doThrow(new RuntimeException("Database Error"))
                .when(notificationRepository).deleteByUserId("user-1");

        assertThrows(RuntimeException.class,
                () -> userCleanupService.deleteUserData("user-1"));

        verify(pushTokenRepository).deleteByUserId("user-1");
    }
}