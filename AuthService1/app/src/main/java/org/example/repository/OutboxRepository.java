package org.example.repository;

import org.example.entities.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

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
