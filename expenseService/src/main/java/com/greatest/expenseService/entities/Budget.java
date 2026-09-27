package com.greatest.expenseService.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "budget", uniqueConstraints = {@UniqueConstraint(columnNames = {"userId", "category", "budget_month", "budget_year"})})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    @Enumerated(EnumType.STRING)
    private CategoryType category;
    @Column(name = "budget_month", nullable = false)
    private Integer month;
    @Column(name = "budget_year", nullable = false)
    private Integer year;

    private BigDecimal amount;

    private Integer lastAlertThreshold;
    @Version
    private Long version;
}
