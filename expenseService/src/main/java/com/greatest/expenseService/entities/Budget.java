package com.greatest.expenseService.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {
                                "userId",
                                "category",
                                "month",
                                "year"
                        }
                )
        }
)
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

    private Integer month;

    private Integer year;

    private BigDecimal amount;

    private Integer lastAlertThreshold;
    @Version
    private Long version;
}
