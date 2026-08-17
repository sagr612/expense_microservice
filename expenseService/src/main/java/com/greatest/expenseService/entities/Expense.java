package com.greatest.expenseService.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "expenses",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_external",
                        columnNames = {"userId","externalId"}
                )
        }
)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;
    @Column(nullable = false)
    private LocalDate transactionDate;
    @Column(nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;



    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExpenseType type;       // "expense" or "income"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private CategoryType category;

    @Column(nullable = false, length = 100)
    private String title;

    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

}