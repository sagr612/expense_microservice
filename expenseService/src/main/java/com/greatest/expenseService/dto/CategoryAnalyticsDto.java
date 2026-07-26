package com.greatest.expenseService.dto;

import com.greatest.expenseService.entities.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class CategoryAnalyticsDto {

    private CategoryType category;
    private BigDecimal totalAmount;
}