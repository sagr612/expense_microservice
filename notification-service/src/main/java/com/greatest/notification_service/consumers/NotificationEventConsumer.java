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

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {
    private final PushTokenService pushTokenService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroup;

    @Transactional
    @KafkaListener(topics = "budget-alerts", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "budgetAlertKafkaListenerContainerFactory")
    public void handleBudgetAlert(BudgetAlertEvent event) {
        String eventId = event.getEventId();
        log.info("Budget alert received for userId: {}", event.getUserId());
        if (eventId == null) {
            log.warn("Event without eventId for user with userId={}", event.getUserId());
            return;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }
        try {
            String title = "⚠️ Budget Alert";
            String body = String.format(
                    "You've used %.0f%% of your %s budget. Spent: ₹%.2f of ₹%.2f",
                    event.getPercentageUsed(),
                    event.getCategory(),
                    event.getSpent(),
                    event.getBudget()
            );
            notificationService.create(event.getUserId(), title, body, NotificationType.BUDGET_ALERT);
            pushTokenService.sendToUser(event.getUserId(), title, body);
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .consumerGroup(consumerGroup)
                    .build());
            log.info("Processed event {} for userId {}", eventId, event.getUserId());
        } catch (DataIntegrityViolationException dup) {
            log.debug("Concurrent processing of {} — swallowing dup marker error", eventId);
        }


    }

    @Transactional
    @KafkaListener(
            topics = "expense.created",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "expenseCreatedKafkaListenerContainerFactory"
    )
    public void handleExpenseCreated(ExpenseCreatedEvent event) {
        log.info("Expense created for userId: {}", event.getUserId());
        String eventId = event.getEventId();
        if (eventId == null) {
            log.warn("Event without eventId for user with userId={}", event.getUserId());
            return;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }
        try {
            Notification notification= notificationService.create(
                    event.getUserId(),
                    "Expense Recorded",
                    "₹" + event.getAmount()
                            + " spent on "
                            + event.getCategory(),
                    NotificationType.EXPENSE_CREATED
            );

            pushTokenService.sendToUser(event.getUserId(), notification.getTitle(),notification.getBody());
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .consumerGroup(consumerGroup)
                    .build());
            log.info("Processed event {} for userId {}", eventId, event.getUserId());
        } catch (DataIntegrityViolationException dup) {
            log.debug("Concurrent processing of {} — swallowing dup marker error", eventId);
        }


    }

}