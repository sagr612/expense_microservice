package sagar.userService.entities;

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
    /* scope by consumer group in case multiple listeners currently in this service only 1 listener
    in future if multiple listeners are add make (eventId, consumerGroup) composite key
     */

    @CreationTimestamp
    private Instant processedAt;
}
