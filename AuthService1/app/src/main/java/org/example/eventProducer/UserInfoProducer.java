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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserInfoProducer {

    private final OutboxClaimService outboxClaimService;
    private final OutboxEventPublisher outboxEventPublisher;

    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:2000}")
    public void publishPendingEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> claimed = outboxClaimService.claimEvents(now, LEASE_DURATION, 10);

        if (claimed.isEmpty()) return;

        log.info("Outbox poller: claimed {} events to publish", claimed.size());

        for (OutboxEvent event : claimed) {
            outboxEventPublisher.processOne(event);
        }
    }


}
