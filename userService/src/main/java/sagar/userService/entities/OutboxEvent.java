package sagar.userService.entities;

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
    private String eventType;        // "USER_DELETED"
    @Column(columnDefinition = "TEXT")
    private String payload;          // JSON

    @CreationTimestamp
    private Instant createdAt;

    private boolean published;       // false until the poller sends it
}