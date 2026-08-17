package com.greatest.expenseService.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseCreatedEvent {
private String eventId;
    private String userId;

    private BigDecimal amount;

    private String category;

    private String externalId;
}