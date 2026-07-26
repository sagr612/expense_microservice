package com.greatest.expenseService.services;

import com.greatest.expenseService.dto.BudgetAlertEvent;
import com.greatest.expenseService.entities.Budget;
import com.greatest.expenseService.entities.Expense;
import com.greatest.expenseService.producer.BudgetAlertProducer;
import com.greatest.expenseService.repository.BudgetRepository;
import com.greatest.expenseService.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertService {

    private final BudgetRepository budgetRepository;

    private final ExpenseRepository expenseRepository;

    private final BudgetAlertProducer producer;

    @Transactional
    public void checkBudget(Expense expense) {
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

        double percentage = spent.divide(budget.getAmount(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();

        Integer threshold = determineThreshold(percentage);

        if (threshold == null) {
            return;
        }

        if (Objects.equals(threshold, budget.getLastAlertThreshold())) {
            return;
        }

        BudgetAlertEvent event = BudgetAlertEvent.builder().userId(expense.getUserId()).category(expense.getCategory().name()).budget(budget.getAmount()).spent(spent).percentageUsed(percentage).alertType(threshold + "_PERCENT").build();

        producer.publish(event);

        budget.setLastAlertThreshold(threshold);

        budgetRepository.save(budget);

        log.info("Budget alert triggered {}% for user {}", threshold, expense.getUserId());
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