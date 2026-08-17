package com.greatest.expenseService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.ExpenseType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpenseDto {
//    private String eventId;
    //    @NotBlank(message = "External ID is required")
    @Size(max = 100, message = "External ID must not exceed 100 characters")
    private String externalId;

    //    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "-999999.99", message = "Amount must be greater than -999999.99")
    @DecimalMax(value = "999999.99", message = "Amount must be less than 999999.99")
    private BigDecimal amount;

    private Long version;


    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code (e.g., USD, INR)")
    private String currency;

    @NotNull(message = "Type is required")
    private ExpenseType type;

    @NotNull(message = "Category is required")
    private CategoryType category;

    private String createdAt;
    @NotNull(message = "Title is required")
    private String title;

    private String note;

    @NotNull(message = "Transaction Date is required")
    private LocalDate transactionDate;


}
