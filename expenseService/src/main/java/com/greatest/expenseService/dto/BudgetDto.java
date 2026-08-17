package com.greatest.expenseService.dto;

import com.greatest.expenseService.entities.CategoryType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Category is required")
    private CategoryType category;

    @NotNull
    @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater  than 0")
    @DecimalMax(value = "999999.99", message = "Amount must be less than 999999.99")
    private BigDecimal amount;
    @NotNull
    @DecimalMin(value = "1", message = "Month must be greater than or equal to 1")
    @DecimalMax(value = "12", message = "Month must be less than or equal to 12")
    private Integer month;
    @NotNull
    @DecimalMin(value = "2000", message = "Year must be greater than or equal to 2000")
    @DecimalMax(value = "9999", message = "Year must be less than or equal to 9999")
    private Integer year;
}