package com.greatest.expenseService.services;

import com.greatest.expenseService.dto.BudgetDto;
import com.greatest.expenseService.dto.BudgetStatusDto;
import com.greatest.expenseService.entities.Budget;
import com.greatest.expenseService.repository.BudgetRepository;
import com.greatest.expenseService.repository.ExpenseRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;

    @Transactional
    public BudgetDto createBudget(String userId, @Valid BudgetDto dto) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("UserId cannot be blank");
        }

        if (dto == null) {
            throw new IllegalArgumentException("Budget request cannot be null");
        }

        if (dto.getCategory() == null) {
            throw new IllegalArgumentException("Category is required");
        }

        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Budget amount must be greater than zero");
        }

        if (dto.getMonth() == null || dto.getMonth() < 1 || dto.getMonth() > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }

        if (dto.getYear() == null || dto.getYear() < 2000 || dto.getYear() > 9999) {
            throw new IllegalArgumentException("Year must be between 2000 and 9999");
        }

        log.info("Creating/updating budget for userId={}, category={}, month={}/{}, amount={}", userId, dto.getCategory(), dto.getMonth(), dto.getYear(), dto.getAmount());
        Budget budget = budgetRepository.findByUserIdAndCategoryAndMonthAndYear(userId, dto.getCategory(), dto.getMonth(), dto.getYear()).orElse(Budget.builder().userId(userId).category(dto.getCategory()).month(dto.getMonth()).year(dto.getYear()).build());

        budget.setAmount(dto.getAmount());

        Budget saved = budgetRepository.save(budget);
        log.info("Budget saved for userId={}, category={}, amount={}", userId, saved.getCategory(), saved.getAmount());

        return BudgetDto.builder().category(saved.getCategory()).amount(saved.getAmount()).month(saved.getMonth()).year(saved.getYear()).build();
    }

    public List<BudgetStatusDto> getBudgetStatus(String userId, Integer month, Integer year) {

        List<Budget> budgets = budgetRepository.findByUserIdAndMonthAndYear(userId, month, year);
        log.info("Calculating budget status for userId={}, month={}/{}", userId, month, year);


        return budgets.stream().map(budget -> {
            BigDecimal spent = expenseRepository.getSpentAmount(userId, budget.getCategory(), month, year);

            if (spent == null) {
                spent = BigDecimal.ZERO;
            }

            BigDecimal budgetAmount = budget.getAmount();

            if (budgetAmount == null || budgetAmount.signum() <= 0) {
                throw new IllegalStateException("Invalid budget amount for budget id " + budget.getId());
            }

            BigDecimal remaining = budgetAmount.subtract(spent);

            double percentage = spent.divide(budgetAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();


            return BudgetStatusDto.builder().category(budget.getCategory()).budget(budgetAmount).spent(spent).remaining(remaining).percentageUsed(percentage).build();


        }).toList();
    }
}
