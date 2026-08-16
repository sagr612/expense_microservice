package sagar.userService.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sagar.userService.dtos.UserDeletedEvent;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.entities.OutboxEvent.OutboxStatus;
import sagar.userService.repository.OutboxRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDeletedProducer {

    private static final int MAX_ATTEMPTS = 10;
    /** Base delay for exponential backoff: 1s, 2s, 4s, 8s, ... capped at MAX_BACKOFF. */
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF  = Duration.ofMinutes(5);
    /** How long we're willing to block on a single Kafka send inside the poller loop. */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(30);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topic-user-deleted.name}")
    private String topic;

    /**
     * Direct fire-and-forget send. NOT used by the outbox path — only kept
     * for callers that want at-most-once semantics (e.g., non-critical events).
     */
    public void publish(UserDeletedEvent event) {
        kafkaTemplate.send(topic, event.getUserId(), event);
    }

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:2000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository
                .findTop50ByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                        OutboxStatus.PENDING, Instant.now());

        if (pending.isEmpty()) return;

        log.debug("Outbox poller: {} events to process", pending.size());

        for (OutboxEvent event : pending) {
            processOne(event);
        }
    }

    /**
     * Runs in its own transaction so a failure on one event doesn't roll back
     * the successful updates for other events in the batch.
     */
    @Transactional
    protected void processOne(OutboxEvent event) {
        try {
            UserDeletedEvent payload = objectMapper.readValue(event.getPayload(), UserDeletedEvent.class);
            kafkaTemplate.send(topic, event.getAggregateId(), payload)
                    .get(SEND_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

            // Success
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublished(true);       // keep legacy flag in sync
            event.setLastError(null);
            outboxRepository.save(event);
            log.debug("Outbox event {} published (attempt {})", event.getId(), event.getAttemptCount() + 1);

        } catch (Exception ex) {
            int newCount = event.getAttemptCount() + 1;
            event.setAttemptCount(newCount);
            event.setLastError(truncate(ex.getMessage(), 1000));

            if (newCount >= MAX_ATTEMPTS) {
                event.setStatus(OutboxStatus.DEAD);
                log.error("Outbox event {} moved to DEAD after {} attempts. Last error: {}",
                        event.getId(), newCount, ex.getMessage(), ex);
            } else {
                Instant next = Instant.now().plus(computeBackoff(newCount));
                event.setNextRetryAt(next);
                log.warn("Outbox event {} failed (attempt {}/{}); next retry at {}. Cause: {}",
                        event.getId(), newCount, MAX_ATTEMPTS, next, ex.getMessage());
            }
            outboxRepository.save(event);
        }
    }

    /** Exponential backoff with cap: BASE * 2^(n-1), capped at MAX_BACKOFF. */
    private Duration computeBackoff(int attempt) {
        long millis = BASE_BACKOFF.toMillis() * (1L << Math.min(attempt - 1, 20));
        return Duration.ofMillis(Math.min(millis, MAX_BACKOFF.toMillis()));
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
