package com.greatest.notification_service.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "processed_events",
        indexes = @Index(name = "idx_processed_at", columnList = "processedAt"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedEvent {

    /** The event's stable identifier as emitted by the producer. */
    @Id
    @Column(length = 100)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String consumerGroup;      // scope by consumer group in case multiple listeners

    @CreationTimestamp
    private Instant processedAt;
}
