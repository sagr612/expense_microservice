package sagar.userService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.entities.OutboxEvent.OutboxStatus;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

    /** Kept for the existing scheduled task before rename. Will be removed once callers migrate. */
    List<OutboxEvent> findTop50ByPublishedFalseOrderByCreatedAtAsc();

    /**
     * Poller query: pick events that are PENDING and whose retry-cooldown has expired,
     * oldest first, batched.
     */
    List<OutboxEvent> findTop50ByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
            OutboxStatus status, Instant now);
}
