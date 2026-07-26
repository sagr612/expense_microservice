package com.greatest.expenseService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Summary {

    private BigDecimal balance;
    private BigDecimal income;
    private BigDecimal expenditure;

    private Long totalTransactions;
    private Long incomeTransactions;
    private Long expenditureTransactions;
}
