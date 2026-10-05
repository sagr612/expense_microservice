package com.greatest.notification_service.consumers;

import com.greatest.notification_service.dtos.BudgetAlertEvent;
import com.greatest.notification_service.dtos.ExpenseCreatedEvent;
import com.greatest.notification_service.entities.Notification;
import com.greatest.notification_service.entities.NotificationType;
import com.greatest.notification_service.repositories.ProcessedEventRepository;
import com.greatest.notification_service.services.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock private NotificationService notificationService;
    @Mock private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "consumerGroup", "notification-service-group");
    }

    @Nested
    class HandleBudgetAlert {
        @Test
        void createsNotification_whenEventIsClaimed() {
            BudgetAlertEvent event = BudgetAlertEvent.builder()
                    .eventId("evt-1")
                    .userId("user-1")
                    .category("FOOD")
                    .percentageUsed(80.0)
                    .spent(BigDecimal.valueOf(800))
                    .budget(BigDecimal.valueOf(1000))
                    .build();
            when(processedEventRepository.insertIfAbsent(eq("evt-1"), anyString(), any(Instant.class)))
                    .thenReturn(1);

            consumer.handleBudgetAlert(event);

            verify(notificationService).create(eq("user-1"), anyString(), anyString(), eq(NotificationType.BUDGET_ALERT));
        }

        @Test
        void skipsAlreadyProcessedEvent_whenClaimReturnsZero() {
            BudgetAlertEvent event = BudgetAlertEvent.builder().eventId("evt-1").userId("u1").build();
            when(processedEventRepository.insertIfAbsent(eq("evt-1"), anyString(), any(Instant.class)))
                    .thenReturn(0);

            consumer.handleBudgetAlert(event);

            verifyNoInteractions(notificationService);
        }

        @Test
        void skipsEventWithoutEventId() {
            BudgetAlertEvent event = BudgetAlertEvent.builder().eventId(null).userId("u1").build();

            consumer.handleBudgetAlert(event);

            verifyNoInteractions(processedEventRepository);
            verifyNoInteractions(notificationService);
        }
    }

    @Nested
    class HandleExpenseCreated {
        @Test
        void createsExpenseNotification_whenEventIsClaimed() {
            ExpenseCreatedEvent event = ExpenseCreatedEvent.builder()
                    .eventId("evt-2").userId("user-1")
                    .amount(BigDecimal.valueOf(50)).category("FOOD").build();
            when(processedEventRepository.insertIfAbsent(eq("evt-2"), anyString(), any(Instant.class)))
                    .thenReturn(1);

            consumer.handleExpenseCreated(event);

            verify(notificationService).create(eq("user-1"), eq("Expense Recorded"), contains("50"), eq(NotificationType.EXPENSE_CREATED));
        }

        @Test
        void skipsAlreadyProcessedEvent_whenClaimReturnsZero() {
            ExpenseCreatedEvent event = ExpenseCreatedEvent.builder().eventId("evt-2").userId("user-1").build();
            when(processedEventRepository.insertIfAbsent(eq("evt-2"), anyString(), any(Instant.class)))
                    .thenReturn(0);

            consumer.handleExpenseCreated(event);

            verifyNoInteractions(notificationService);
        }
    }
}
