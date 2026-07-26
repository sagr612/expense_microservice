package com.greatest.notification_service.consumers;

import com.greatest.notification_service.dtos.BudgetAlertEvent;
import com.greatest.notification_service.dtos.ExpenseCreatedEvent;
import com.greatest.notification_service.entities.NotificationType;
import com.greatest.notification_service.services.NotificationService;
import com.greatest.notification_service.services.PushTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final PushTokenService pushTokenService;
    private final NotificationService notificationService;

    // Listens to budget alerts from expenseService
    @KafkaListener(
            topics = "budget-alerts",
            groupId = "notification-service-group",
            containerFactory = "budgetAlertKafkaListenerContainerFactory"
    )
    public void handleBudgetAlert(BudgetAlertEvent event) {
        log.info("Budget alert received for userId: {}", event.getUserId());

        String title = "⚠️ Budget Alert";
        String body = String.format(
                "You've used %.0f%% of your %s budget. Spent: ₹%.2f of ₹%.2f",
                event.getPercentageUsed(),
                event.getCategory(),
                event.getSpent(),
                event.getBudget()
        );
        notificationService.create(
                event.getUserId(),
                title,
                body,
                NotificationType.BUDGET_ALERT
        );
        pushTokenService.sendToUser(event.getUserId(), title, body);
    }

    // Listens to expense created events
//    @KafkaListener(
//            topics = "expense.created",
//            groupId = "notification-service-group",
//            containerFactory = "expenseCreatedKafkaListenerContainerFactory"
//    )
//    public void handleExpenseCreated(ExpenseCreatedEvent event) {
//        log.info("Expense created for userId: {}", event.getUserId());
//
//        notificationService.create(
//                event.getUserId(),
//                "Expense Recorded",
//                "₹" + event.getAmount()
//                        + " spent on "
//                        + event.getCategory(),
//                NotificationType.EXPENSE_CREATED
//        );
////        pushTokenService.sendToUser(event.getUserId(), title, body);
//    }
    @KafkaListener(
            topics = "expense.created",
            groupId = "notification-service-group",
            containerFactory = "expenseCreatedKafkaListenerContainerFactory"
    )
    public void consume(
            ExpenseCreatedEvent event,
            ConsumerRecord<String, ExpenseCreatedEvent> record
    ) {
        log.info("traceId={}", MDC.get("traceId"));
        record.headers().forEach(h ->
                log.info("HEADER {} = {}", h.key(),
                        new String(h.value()))
        );

        log.info("Event received");
    }
}