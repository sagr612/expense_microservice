package sagar.userService.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "failed_events")
@Getter
@Setter
public class FailedEvents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topic;

    private Integer partition;
    @Column(name = "record_offset")
    private Long offset;

    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(length = 1000)
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    @Enumerated(EnumType.STRING)
    private FailedEventStatus status;

    private Integer retryCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}