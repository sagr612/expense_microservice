package com.greatest.expenseService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MonthlyAnalyticsDto {
    private Integer year;
    private Integer month;
    private BigDecimal totalExpense;
    private BigDecimal totalIncome;
}