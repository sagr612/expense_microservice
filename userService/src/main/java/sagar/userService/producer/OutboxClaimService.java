package sagar.userService.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.entities.OutboxEvent.OutboxStatus;
import sagar.userService.repository.OutboxRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxClaimService {

    private final OutboxRepository outboxRepository;

    @Transactional
    public List<OutboxEvent> claimEvents(Instant now, Duration leaseDuration, int batchSize) {
        List<OutboxEvent> candidates = outboxRepository.findClaimableEvents(now, batchSize);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Instant leaseExpiry = now.plus(leaseDuration);
        for (OutboxEvent event : candidates) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setLockedUntil(leaseExpiry);
        }

        return outboxRepository.saveAll(candidates);
    }
}
