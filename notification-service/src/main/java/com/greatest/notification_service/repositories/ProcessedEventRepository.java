package com.greatest.notification_service.repositories;

import com.greatest.notification_service.entities.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    @Modifying
    @Query("""
            DELETE FROM ProcessedEvent p  WHERE p.processedAt < :cutoff
            """)
    int deleteByProcessedAtBefore(@Param("cutoff") Instant cutoff);


    @Modifying
    @Query(value = """
            INSERT INTO  processed_events (event_id, consumer_group, processed_at)
                                    VALUES (:eventId, :consumerGroup, :processedAt)
                                    ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("consumerGroup") String consumerGroup,
                       @Param("processedAt") Instant processedAt);

}
