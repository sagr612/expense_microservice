package com.greatest.expenseService.producer;

import com.greatest.expenseService.entities.OutboxEvent;
import com.greatest.expenseService.entities.OutboxEvent.OutboxStatus;
import com.greatest.expenseService.repository.OutboxRepository;
import com.greatest.expenseService.services.OutboxClaimService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {
    private final OutboxClaimService outboxClaimService;
    private final OutboxEventPublisher outboxEventPublisher;

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:2000}")
    public void publishPendingEvents() {
        List<OutboxEvent> claimed = outboxClaimService.claimEvents(Instant.now(), Duration.ofMinutes(1), 50);

        if (claimed.isEmpty()) return;

        log.debug("Outbox poller: {} events to process", claimed.size());

        for (OutboxEvent event : claimed) {
            outboxEventPublisher.processOne(event);
        }
    }
}
