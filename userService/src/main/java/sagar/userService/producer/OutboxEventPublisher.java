package sagar.userService.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sagar.userService.dtos.UserDeletedEvent;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.repository.OutboxRepository;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF  = Duration.ofMinutes(5);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(30);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topic-user-deleted.name}")
    private String topic;

    @Transactional
    public void processOne(OutboxEvent event) {
        try {
            UserDeletedEvent payload = objectMapper.readValue(event.getPayload(), UserDeletedEvent.class);
            kafkaTemplate.send(topic, event.getAggregateId(), payload)
                    .get(SEND_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

            // Success
            event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
            event.setPublished(true);       // keep legacy flag in sync
            event.setLastError(null);
            outboxRepository.save(event);
            log.debug("Outbox event {} published (attempt {})", event.getId(), event.getAttemptCount() + 1);

        } catch (Exception ex) {
            int newCount = event.getAttemptCount() + 1;
            event.setAttemptCount(newCount);
            event.setLastError(truncate(ex.getMessage(), 1000));

            if (newCount >= MAX_ATTEMPTS) {
                event.setStatus(OutboxEvent.OutboxStatus.DEAD);
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

    private Duration computeBackoff(int attempt) {
        long millis = BASE_BACKOFF.toMillis() * (1L << Math.min(attempt - 1, 20));
        return Duration.ofMillis(Math.min(millis, MAX_BACKOFF.toMillis()));
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
