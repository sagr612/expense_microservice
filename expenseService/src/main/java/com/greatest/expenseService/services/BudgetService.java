package com.greatest.expenseService.services;

import com.greatest.expenseService.dto.BudgetDto;
import com.greatest.expenseService.dto.BudgetStatusDto;
import com.greatest.expenseService.entities.Budget;
import com.greatest.expenseService.repository.BudgetRepository;
import com.greatest.expenseService.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    @Transactional
    public BudgetDto createBudget(
            String userId,
            BudgetDto dto
    ) {

        Budget budget =
                budgetRepository
                        .findByUserIdAndCategoryAndMonthAndYear(
                                userId,
                                dto.getCategory(),
                                dto.getMonth(),
                                dto.getYear()
                        )
                        .orElse(
                                Budget.builder()
                                        .userId(userId)
                                        .category(dto.getCategory())
                                        .month(dto.getMonth())
                                        .year(dto.getYear())
                                        .build()
                        );

        budget.setAmount(dto.getAmount());

        budgetRepository.save(budget);

        return dto;
    }

    public List<BudgetStatusDto> getBudgetStatus(
            String userId,
            Integer month,
            Integer year
    ) {

        List<Budget> budgets =
                budgetRepository.findByUserIdAndMonthAndYear(
                        userId,
                        month,
                        year
                );

        return budgets.stream()
                .map(budget -> {

                    BigDecimal spent =
                            expenseRepository.getSpentAmount(
                                    userId,
                                    budget.getCategory(),
                                    month,
                                    year
                            );

                    BigDecimal remaining =
                            budget.getAmount().subtract(spent);

                    double percentage =
                            spent
                                    .divide(
                                            budget.getAmount(),
                                            4,
                                            RoundingMode.HALF_UP
                                    )
                                    .multiply(BigDecimal.valueOf(100))
                                    .doubleValue();

                    return BudgetStatusDto.builder()
                            .category(budget.getCategory())
                            .budget(budget.getAmount())
                            .spent(spent)
                            .remaining(remaining)
                            .percentageUsed(percentage)
                            .build();

                })
                .toList();
    }
}
