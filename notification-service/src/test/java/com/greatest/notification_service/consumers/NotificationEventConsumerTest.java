package com.greatest.notification_service.consumers;

import com.greatest.notification_service.dtos.BudgetAlertEvent;
import com.greatest.notification_service.dtos.ExpenseCreatedEvent;
import com.greatest.notification_service.entities.Notification;
import com.greatest.notification_service.entities.NotificationType;
import com.greatest.notification_service.entities.ProcessedEvent;
import com.greatest.notification_service.repositories.ProcessedEventRepository;
import com.greatest.notification_service.services.NotificationService;
import com.greatest.notification_service.services.PushTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock private PushTokenService pushTokenService;
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
        void createsNotification_andSendsPush_andRecordsMarker() {
            BudgetAlertEvent event = BudgetAlertEvent.builder()
                    .eventId("evt-1")
                    .userId("user-1")
                    .category("FOOD")
                    .percentageUsed(80.0)
                    .spent(BigDecimal.valueOf(800))
                    .budget(BigDecimal.valueOf(1000))
                    .build();
            when(processedEventRepository.existsById("evt-1")).thenReturn(false);

            consumer.handleBudgetAlert(event);

            verify(notificationService).create(eq("user-1"), anyString(), anyString(), eq(NotificationType.BUDGET_ALERT));
            verify(pushTokenService).sendToUser(eq("user-1"), anyString(), anyString());
            verify(processedEventRepository).save(any(ProcessedEvent.class));
        }

        @Test
        void skipsAlreadyProcessedEvent() {
            BudgetAlertEvent event = BudgetAlertEvent.builder().eventId("evt-1").userId("u1").build();
            when(processedEventRepository.existsById("evt-1")).thenReturn(true);

            consumer.handleBudgetAlert(event);

            verifyNoInteractions(notificationService, pushTokenService);
            verify(processedEventRepository, never()).save(any());
        }

        @Test
        void skipsEventWithoutEventId() {
            BudgetAlertEvent event = BudgetAlertEvent.builder().eventId(null).userId("u1").build();

            consumer.handleBudgetAlert(event);

            verifyNoInteractions(notificationService, pushTokenService, processedEventRepository);
            // note: existsById is never called because we short-circuit before it
        }

        @Test
        void swallowsDuplicateMarkerInsert_whenTwoConsumersRace() {
            BudgetAlertEvent event = BudgetAlertEvent.builder().eventId("evt-1").userId("u1")
                    .category("F").percentageUsed(50.0)
                    .spent(BigDecimal.ONE).budget(BigDecimal.TEN).build();
            when(processedEventRepository.existsById("evt-1")).thenReturn(false);
            when(processedEventRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

            // must NOT propagate — dedup race is harmless
            assertThatCode(() -> consumer.handleBudgetAlert(event)).doesNotThrowAnyException();
        }
    }

    @Nested
    class HandleExpenseCreated {
        @Test
        void createsExpenseNotification_andSendsPush() {
            ExpenseCreatedEvent event = ExpenseCreatedEvent.builder()
                    .eventId("evt-2").userId("user-1")
                    .amount(BigDecimal.valueOf(50)).category("FOOD").build();
            when(processedEventRepository.existsById("evt-2")).thenReturn(false);
            when(notificationService.create(any(), any(), any(), any()))
                    .thenReturn(Notification.builder().title("Expense Recorded").body("₹50 spent on FOOD").build());

            consumer.handleExpenseCreated(event);

            verify(notificationService).create(eq("user-1"), eq("Expense Recorded"), contains("50"), eq(NotificationType.EXPENSE_CREATED));
            verify(pushTokenService).sendToUser(eq("user-1"), eq("Expense Recorded"), anyString());
            verify(processedEventRepository).save(any(ProcessedEvent.class));
        }
    }
}
