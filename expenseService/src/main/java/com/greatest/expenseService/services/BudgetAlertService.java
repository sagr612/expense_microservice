package com.greatest.expenseService.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.expenseService.dto.BudgetAlertEvent;
import com.greatest.expenseService.entities.Budget;
import com.greatest.expenseService.entities.Expense;
import com.greatest.expenseService.entities.OutboxEvent;
import com.greatest.expenseService.repository.BudgetRepository;
import com.greatest.expenseService.repository.ExpenseRepository;
import com.greatest.expenseService.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertService {

    private final BudgetRepository budgetRepository;

    private final ExpenseRepository expenseRepository;

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void checkBudget(Expense expense) {
        if (expense.getTransactionDate() == null) {
            log.warn("Skipping budget check because transactionDate is null");
            return;
        }
        int month = expense.getTransactionDate().getMonthValue();
        int year = expense.getTransactionDate().getYear();

        Budget budget = budgetRepository.findByUserIdAndCategoryAndMonthAndYear(expense.getUserId(), expense.getCategory(), month, year).orElse(null);

        if (budget == null) {
            return;
        }

        BigDecimal spent = expenseRepository.getSpentAmount(expense.getUserId(), expense.getCategory(), month, year);

        if (spent == null) {
            spent = BigDecimal.ZERO;
        }
        BigDecimal budgetAmount = budget.getAmount();

        if (budgetAmount == null || budgetAmount.signum() <= 0) {
            log.error(
                    "Skipping budget alert because budget {} has invalid amount {}",
                    budget.getId(),
                    budgetAmount
            );
            return;
        }

        double percentage = spent
                .divide(budgetAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        Integer threshold = determineThreshold(percentage);

        if (threshold == null) {
            return;
        }

        if (Objects.equals(threshold, budget.getLastAlertThreshold())) {
            return;
        }

        BudgetAlertEvent event = BudgetAlertEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(expense.getUserId()).
                category(expense.getCategory().name())
                .budget(budgetAmount)
                .spent(spent)
                .percentageUsed(percentage)
                .alertType(threshold + "_PERCENT").build();


        outboxRepository.save(OutboxEvent.builder()
                .aggregateId(expense.getUserId())
                .eventType("BUDGET_ALERT")
                .payload(toJson(event))
                .published(false)
                .build());

        budget.setLastAlertThreshold(threshold);

        budgetRepository.save(budget);

        log.info("Budget alert triggered {}% for user {}", threshold, expense.getUserId());
    }
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event for outbox: " + obj, e);
        }
    }


    private Integer determineThreshold(double percentage) {

        if (percentage >= 100) {
            return 100;
        }

        if (percentage >= 80) {
            return 80;
        }

        if (percentage >= 50) {
            return 50;
        }

        return null;
    }
}