package org.example.repository;

import org.example.entities.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop50ByPublishedFalseOrderByCreatedAtAsc();

    List<OutboxEvent> findTop50ByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
            OutboxEvent.OutboxStatus status, Instant now);

}
