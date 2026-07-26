package com.greatest.expenseService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class MonthlyAnalyticsDto {

    private Integer year;

    private Integer month;

    private BigDecimal totalAmount;
}