package com.greatest.expenseService.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.expenseService.dto.BudgetAlertEvent;
import com.greatest.expenseService.dto.ExpenseCreatedEvent;
import com.greatest.expenseService.entities.OutboxEvent;
import com.greatest.expenseService.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(30);

    @Value("${spring.kafka.topic-expense-created.name}")
    private String expenseCreatedTopic;

    @Value("${spring.kafka.topic-budget-alert.name}")
    private String budgetAlertTopic;

    @Transactional
    public void processOne(OutboxEvent event) {
        try {
            Object payload;
            String topic;

            switch (event.getEventType()) {
                case "EXPENSE_CREATED" -> {
                    payload = objectMapper.readValue(event.getPayload(), ExpenseCreatedEvent.class);
                    ((ExpenseCreatedEvent) payload).setEventId(event.getId());
                    topic = expenseCreatedTopic;
                }
                case "BUDGET_ALERT" -> {
                    payload = objectMapper.readValue(event.getPayload(), BudgetAlertEvent.class);
                    ((BudgetAlertEvent) payload).setEventId(event.getId());
                    topic = budgetAlertTopic;
                }
                default -> {
                    log.error("Unknown event type {} in outbox — moving to DEAD", event.getEventType());
                    event.setStatus(OutboxEvent.OutboxStatus.DEAD);
                    event.setLastError("Unknown event type: " + event.getEventType());
                    outboxRepository.save(event);
                    return;
                }
            }

            kafkaTemplate.send(topic, event.getAggregateId(), payload)
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
            event.setPublished(true);
            event.setLastError(null);
            event.setLockedUntil(null);
            outboxRepository.save(event);
            log.debug("Outbox event {} published (attempt {})", event.getId(), event.getAttemptCount() + 1);

        } catch (Exception ex) {
            int newCount = event.getAttemptCount() + 1;
            event.setAttemptCount(newCount);
            event.setLastError(truncate(ex.getMessage(), 1000));
            event.setLockedUntil(null);
            if (newCount >= MAX_ATTEMPTS) {
                event.setStatus(OutboxEvent.OutboxStatus.DEAD);
                log.error("Outbox event {} moved to DEAD after {} attempts. Last error: {}",
                        event.getId(), newCount, ex.getMessage(), ex);
            } else {
                Instant next = Instant.now().plus(computeBackoff(newCount));
                event.setStatus(OutboxEvent.OutboxStatus.PENDING);
                event.setNextRetryAt(next);
                log.warn("Outbox event {} failed (attempt {}/{}); next retry at {}. Cause: {}",
                        event.getId(), newCount, MAX_ATTEMPTS, next, ex.getMessage());
            }
            outboxRepository.save(event);
        }
    }

    private Duration computeBackoff(int attempt) {
        long millis = BASE_BACKOFF.toMillis() * (1L << Math.min(attempt - 1, 20));
        return Duration.ofMillis(Math.min(millis, MAX_BACKOFF.toMillis()));
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
