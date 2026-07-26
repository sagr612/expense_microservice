package com.greatest.expenseService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class SummaryProjection {

    private BigDecimal income;
    private BigDecimal expenditure;
    private Long totalTransactions;
    private Long incomeTransactions;
    private Long expenditureTransactions;
}