package com.greatest.expenseService.dto;

import com.greatest.expenseService.entities.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BudgetStatusDto {

    private CategoryType category;

    private BigDecimal budget;

    private BigDecimal spent;

    private BigDecimal remaining;

    private Double percentageUsed;
}