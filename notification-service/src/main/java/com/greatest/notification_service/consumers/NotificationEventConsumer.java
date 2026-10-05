package com.greatest.notification_service.consumers;

import com.greatest.notification_service.dtos.BudgetAlertEvent;
import com.greatest.notification_service.dtos.ExpenseCreatedEvent;
import com.greatest.notification_service.entities.Notification;
import com.greatest.notification_service.entities.NotificationType;
import com.greatest.notification_service.entities.ProcessedEvent;
import com.greatest.notification_service.repositories.ProcessedEventRepository;
import com.greatest.notification_service.services.NotificationService;
import com.greatest.notification_service.services.PushTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroup;

    @Transactional
    @KafkaListener(
            topics = "${spring.kafka.topic-budget-alert.name:budget-alerts}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}",
            containerFactory = "budgetAlertKafkaListenerContainerFactory")
    public void handleBudgetAlert(BudgetAlertEvent event) {
        if (event == null || event.getUserId() == null || event.getEventId() == null) {
            log.warn("Skipping invalid budget alert event: payload, userId, or eventId is null");
            return;
        }
        String eventId = event.getEventId();
        log.info("Budget alert received for userId: {}", event.getUserId());

        int claimed = processedEventRepository.insertIfAbsent(eventId, consumerGroup, Instant.now());

        if (claimed == 0) {
            log.debug("Skipping already-processed budget alert event {}", eventId);
            return;
        }
        String title = "⚠️ Budget Alert";
        String body = String.format("You've used %.0f%% of your %s budget. Spent: ₹%.2f of ₹%.2f", event.getPercentageUsed(), event.getCategory(), event.getSpent(), event.getBudget());
        notificationService.create(event.getUserId(), title, body, NotificationType.BUDGET_ALERT);
//            processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).consumerGroup(consumerGroup).build());
        log.info("Processed event {} for userId {}", eventId, event.getUserId());


    }

    @Transactional
    @KafkaListener(topics = "${spring.kafka.topic-expense-created.name:expense.created}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}",
            containerFactory = "expenseCreatedKafkaListenerContainerFactory")
    public void handleExpenseCreated(ExpenseCreatedEvent event) {
        if (event == null || event.getUserId() == null || event.getEventId() == null) {
            log.warn("Skipping invalid expense created event: payload, userId, or eventId is null");
            return;
        }
        String eventId = event.getEventId();
        log.info("Expense created for userId: {}", event.getUserId());


        int claimed = processedEventRepository.insertIfAbsent(eventId, consumerGroup, Instant.now());

        if (claimed == 0) {
            log.debug("Skipping already-processed expense created event {}", eventId);
            return;
        }
        String title = "Expense Recorded";
        String body = String.format("₹%.2f spent on %s", event.getAmount(), event.getCategory());
        notificationService.create(event.getUserId(), title, body, NotificationType.EXPENSE_CREATED);
//            processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).consumerGroup(consumerGroup).build());
        log.info("Processed event {} for userId {}", eventId, event.getUserId());
    }

}