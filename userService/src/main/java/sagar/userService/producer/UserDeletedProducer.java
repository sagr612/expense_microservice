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
    private final OutboxEventPublisher outboxEventPublisher;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxRepository outboxRepository;

    @Value("${spring.kafka.topic-user-deleted.name}")
    private String topic;

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
            outboxEventPublisher.processOne(event);
        }
    }


}
