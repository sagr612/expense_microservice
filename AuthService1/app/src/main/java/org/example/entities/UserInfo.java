package org.example.entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users")
public class UserInfo {
    @Id
    @Column(name = "user_id")
    private String userId;


    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<UserRole> roles = new HashSet<>();

    @Column(nullable = false, unique = true)
    private Long phoneNumber;
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = true, updatable = false) // Change to nullable = true
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = true) // Change to nullable = true
    @LastModifiedDate
    private Instant updatedAt;

    // Add PrePersist to set defaults
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
