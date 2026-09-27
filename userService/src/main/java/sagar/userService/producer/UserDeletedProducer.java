package sagar.userService.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import sagar.userService.dtos.event.UserDeletedEvent;
import sagar.userService.entities.OutboxEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDeletedProducer {
    private final OutboxEventPublisher outboxEventPublisher;
    private final OutboxClaimService outboxClaimService;
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);




    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:2000}")
    public void publishPendingEvents() {
        List<OutboxEvent> claimed = outboxClaimService.claimEvents(Instant.now(), LEASE_DURATION, 10);

        if (claimed.isEmpty()) return;


        for (OutboxEvent event : claimed) {
            outboxEventPublisher.processOne(event);
        }
    }


}
