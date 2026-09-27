package com.greatest.expenseService.repository;

import com.greatest.expenseService.entities.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent> findTop50ByPublishedFalseOrderByCreatedAtAsc();

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE (status = 'PENDING' AND next_retry_at <= :now)
            OR (status = 'PROCESSING' AND locked_until <= :now)
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findClaimableEvents(@Param("now") Instant now, @Param("limit") int limit);

}
