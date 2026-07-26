package com.greatest.expenseService.dto;

import com.greatest.expenseService.entities.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
public class CategoryAnalyticsResponse {

    private Map<CategoryType, BigDecimal> analytics;
}