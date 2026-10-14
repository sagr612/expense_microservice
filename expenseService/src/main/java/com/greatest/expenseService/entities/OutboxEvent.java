package com.greatest.expenseService.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_pending",
                        columnList = "status, next_retry_at, created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String aggregateId;      // userId
    private String eventType;        // "USER_DELETED"

    @Column(columnDefinition = "TEXT")
    private String payload;          // JSON

    @CreationTimestamp
    private Instant createdAt;

    /** Retained for backward compatibility with existing rows / dashboards. */
    private boolean published;

    /** Number of publish attempts made so far. */
    @Column(nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    /** Earliest time this event is eligible to be published again. */
    @Column(nullable = false)
    @Builder.Default
    private Instant nextRetryAt = Instant.now();

    /** Last exception message, for observability. */
    @Column(columnDefinition = "TEXT")
    private String lastError;

    private Instant lockedUntil;

    @Column(name = "traceparent", length = 255)
    private String traceparent;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    public enum OutboxStatus {
        PENDING,   // waiting to be published (or being retried)
        PROCESSING, // leased by worker pool
        PUBLISHED, // successfully sent to Kafka
        DEAD       // exceeded max attempts, needs manual review
    }
}
