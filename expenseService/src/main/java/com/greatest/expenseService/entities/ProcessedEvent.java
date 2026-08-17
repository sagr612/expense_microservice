package com.greatest.expenseService.entities;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "processed_events",
        indexes = @Index(name = "idx_processed_at", columnList = "processedAt"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedEvent {

    @Id
    @Column(length = 100)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String consumerGroup;

    @CreationTimestamp
    private Instant processedAt;
}

