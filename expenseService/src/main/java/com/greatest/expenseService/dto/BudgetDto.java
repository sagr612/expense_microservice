package com.greatest.expenseService.dto;

import com.greatest.expenseService.entities.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetDto {

    private CategoryType category;

    private BigDecimal amount;

    private Integer month;

    private Integer year;
}