package sagar.userService.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.repository.OutboxRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OutboxAdminService {
    private final OutboxRepository outboxRepository;

    public void reviveEvent(String eventId) {
        OutboxEvent event = outboxRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + eventId));
        if (event.getStatus() != OutboxEvent.OutboxStatus.DEAD) {
            throw new IllegalStateException("Event is not DEAD; nothing to revive");
        }
        event.setStatus(OutboxEvent.OutboxStatus.PENDING);
        event.setAttemptCount(0);
        event.setNextRetryAt(Instant.now());
        event.setLastError(null);
        outboxRepository.save(event);
    }
}
