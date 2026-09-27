package sagar.userService.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                // Poller hot-path: WHERE status='PENDING' AND next_retry_at <= now ORDER BY created_at
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

    private boolean published;

    @Column(nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Instant nextRetryAt = Instant.now();

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    public enum OutboxStatus {
        PENDING,
        PROCESSING,
        PUBLISHED,
        DEAD
    }

    private Instant lockedUntil;
}
