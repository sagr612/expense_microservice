package com.greatest.expenseService.consumer;

import com.greatest.expenseService.dto.UserDeletedEvent;
import com.greatest.expenseService.services.UserCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserDeletedConsumerTest {

    @Mock
    private UserCleanupService userCleanupService;

    @InjectMocks
    private UserDeletedConsumer userDeletedConsumer;

    @Test
    void consume_shouldDelegateToUserCleanupService() {
        UserDeletedEvent event = UserDeletedEvent.builder()
                .userId("user-123")
                .build();

        userDeletedConsumer.consume(event);

        verify(userCleanupService).deleteUserData("user-123");
    }
}
