package com.greatest.notification_service.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "push_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 100)
    private String userId;

    @Column(nullable = false, unique = true)
    private String expoToken;   // e.g. ExponentPushToken[7Q3kxJIjDTUeak-EQuF03T]

    @UpdateTimestamp
    private Instant updatedAt;
}
