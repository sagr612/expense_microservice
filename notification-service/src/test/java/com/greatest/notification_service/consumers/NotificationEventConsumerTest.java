package com.greatest.notification_service.consumers;

import com.greatest.notification_service.dtos.BudgetAlertEvent;
import com.greatest.notification_service.dtos.ExpenseCreatedEvent;
import com.greatest.notification_service.entities.NotificationType;
import com.greatest.notification_service.services.NotificationService;
import com.greatest.notification_service.services.PushTokenService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private PushTokenService pushTokenService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventConsumer consumer;

    @Nested
    class HandleBudgetAlert {

        @Test
        void createsNotification_andSendsPush_withFormattedBody() {
            BudgetAlertEvent event = BudgetAlertEvent.builder()
                    .userId("user-1")
                    .category("FOOD")
                    .percentageUsed(80.0)
                    .spent(BigDecimal.valueOf(800))
                    .budget(BigDecimal.valueOf(1000))
                    .alertType("80_PERCENT")
                    .build();

            consumer.handleBudgetAlert(event);

            ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            verify(notificationService).create(
                    eq("user-1"),
                    titleCaptor.capture(),
                    bodyCaptor.capture(),
                    eq(NotificationType.BUDGET_ALERT));

            assertThat(titleCaptor.getValue()).contains("Budget Alert");
            assertThat(bodyCaptor.getValue()).contains("80%");
            assertThat(bodyCaptor.getValue()).contains("FOOD");

            verify(pushTokenService).sendToUser(eq("user-1"), anyString(), anyString());
        }

        @Test
        void sendsCorrectUserId_toPushTokenService() {
            BudgetAlertEvent event = BudgetAlertEvent.builder()
                    .userId("user-42")
                    .category("TRANSPORT")
                    .percentageUsed(100.0)
                    .spent(BigDecimal.valueOf(500))
                    .budget(BigDecimal.valueOf(500))
                    .alertType("100_PERCENT")
                    .build();

            consumer.handleBudgetAlert(event);

            verify(pushTokenService).sendToUser(eq("user-42"), anyString(), anyString());
        }

        @Test
        void createsNotification_beforeSendingPush() {
            BudgetAlertEvent event = BudgetAlertEvent.builder()
                    .userId("user-1")
                    .category("FOOD")
                    .percentageUsed(50.0)
                    .spent(BigDecimal.valueOf(500))
                    .budget(BigDecimal.valueOf(1000))
                    .alertType("50_PERCENT")
                    .build();

            consumer.handleBudgetAlert(event);

            var inOrder = inOrder(notificationService, pushTokenService);
            inOrder.verify(notificationService).create(any(), any(), any(), any());
            inOrder.verify(pushTokenService).sendToUser(any(), any(), any());
        }
    }

    @Nested
    class ConsumeExpenseCreated {

        @Test
        void doesNotThrow_andDoesNotTouchServices() {
            // current implementation only logs traceId/headers — no service interactions
            ExpenseCreatedEvent event = ExpenseCreatedEvent.builder()
                    .userId("user-1")
                    .amount(BigDecimal.valueOf(50))
                    .category("FOOD")
                    .externalId("ext-1")
                    .build();

            ConsumerRecord<String, ExpenseCreatedEvent> record = new ConsumerRecord<>("expense.created", 0, 0L, "user-1", event);

            assertThatCode(() -> consumer.consume(event, record))
                    .doesNotThrowAnyException();

            verifyNoInteractions(notificationService, pushTokenService);
        }
    }
}