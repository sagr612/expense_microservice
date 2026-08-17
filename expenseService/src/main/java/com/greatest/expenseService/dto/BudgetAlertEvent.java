package com.greatest.expenseService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BudgetAlertEvent {
private String eventId;
    private String userId;

    private String category;

    private Double percentageUsed;

    private BigDecimal spent;

    private BigDecimal budget;

    private String alertType;
}