package org.example.eventProducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entities.OutboxEvent;
import org.example.repository.OutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserInfoProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;

    @Value("${spring.kafka.topic-json.name}")
    private String topic;

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF  = Duration.ofMinutes(5);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(30);

//
//    @Scheduled(fixedDelay = 5000)
//    public void publishPendingEvents() {
//        log.info("Publishing pending events");
//        List<OutboxEvent> pending = outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();
//        for (OutboxEvent event : pending) {
//            try {
//                UserInfoEvent payload = objectMapper.readValue(event.getPayload(), UserInfoEvent.class);
//              payload.setEventId(event.getId());
//                kafkaTemplate.send(topic, event.getAggregateId(), payload).get(); //can make scheduler slow so increased thread pool
//                event.setPublished(true);
//                outboxRepository.save(event);
//            } catch (Exception e) {
//                log.error("Failed to publish outbox event {}", event.getId(), e);
//            }
//        }
//    }



    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:2000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository
                .findTop50ByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                        OutboxEvent.OutboxStatus.PENDING, Instant.now());

        if (pending.isEmpty()) return;

        log.debug("Outbox poller: {} events to process", pending.size());

        for (OutboxEvent event : pending) {
            processOne(event);
        }
    }

    protected void processOne(OutboxEvent event) {
        try {
            UserInfoEvent payload = objectMapper.readValue(event.getPayload(), UserInfoEvent.class);
            payload.setEventId(event.getId());

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
