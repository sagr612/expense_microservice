package org.example.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
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
    private String eventType;        // "USER_CREATED"
    @Column(columnDefinition = "TEXT")
    private String payload;          // JSON
    @CreationTimestamp
    private Instant createdAt;
    private boolean published;
    @Column(nullable = false) @Builder.Default private int attemptCount = 0;
    @Column(nullable = false) @Builder.Default private Instant nextRetryAt = Instant.now();
    @Column(columnDefinition = "TEXT") private String lastError;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    public enum OutboxStatus { PENDING, PUBLISHED, DEAD }

}

