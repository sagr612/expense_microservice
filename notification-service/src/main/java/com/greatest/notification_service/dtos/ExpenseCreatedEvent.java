package com.greatest.notification_service.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseCreatedEvent {
    private String userId;
    private BigDecimal amount;
    private String category;
    private String externalId;
}