package com.greatest.notification_service.consumers;

import com.greatest.notification_service.dtos.UserDeletedEvent;
import com.greatest.notification_service.services.UserCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDeletedConsumerTest {

    @Mock
    private UserCleanupService userCleanupService;

    @InjectMocks
    private UserDeletedConsumer consumer;

    @Test
    void shouldCleanupUserWhenEventReceived() {
        UserDeletedEvent event = UserDeletedEvent.builder().userId("user-1").build();

        consumer.consume(event);

        verify(userCleanupService).deleteUserData("user-1");
    }

    @Test
    void shouldCleanupOnlyOnce() {
        UserDeletedEvent event = UserDeletedEvent.builder().userId("user-100").build();

        consumer.consume(event);

        verify(userCleanupService, times(1)).deleteUserData("user-100");
    }

    @Test
    void shouldPassCorrectUserId() {
        UserDeletedEvent event = UserDeletedEvent.builder().userId("ABC123").build();

        consumer.consume(event);

        verify(userCleanupService).deleteUserData("ABC123");
    }

    @Test
    void shouldPropagateException_forErrorHandlerToRoute() {
        UserDeletedEvent event = UserDeletedEvent.builder().userId("user-1").build();

        doThrow(new RuntimeException("Database Error"))
                .when(userCleanupService)
                .deleteUserData(anyString());

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> consumer.consume(event)
        );
    }
}