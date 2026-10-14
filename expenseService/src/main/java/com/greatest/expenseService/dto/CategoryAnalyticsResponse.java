package com.greatest.expenseService.dto;

import com.greatest.expenseService.entities.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryAnalyticsResponse {

    private Map<CategoryType, BigDecimal> expenses;
    private Map<CategoryType, BigDecimal> income;

}