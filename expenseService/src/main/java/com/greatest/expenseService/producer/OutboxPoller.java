package com.greatest.expenseService.producer;

import com.greatest.expenseService.entities.OutboxEvent;
import com.greatest.expenseService.entities.OutboxEvent.OutboxStatus;
import com.greatest.expenseService.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {
    private final OutboxRepository outboxRepository;
    private final OutboxEventPublisher outboxEventPublisher;

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
