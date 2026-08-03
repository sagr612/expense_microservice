package com.greatest.expenseService.dto;

import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.Expense;
import com.greatest.expenseService.entities.ExpenseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.greatest.expenseService.repository.ExpenseRepository;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ExpenseSpecificationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ExpenseRepository expenseRepository;

    private Expense expense(String userId, CategoryType category, ExpenseType type, BigDecimal amount, LocalDate date) {
        return Expense.builder()
                .userId(userId).category(category).type(type)
                .amount(amount).currency("INR").title("t")
                .transactionDate(date)
                .build();
    }

    @Test
    void combinesCategoryAndAmountFilters_withAndSemantics() {
        entityManager.persist(expense("user-1", CategoryType.FOOD, ExpenseType.EXPENSE,
                BigDecimal.valueOf(100), LocalDate.now()));
        entityManager.persist(expense("user-1", CategoryType.FOOD, ExpenseType.EXPENSE,
                BigDecimal.valueOf(500), LocalDate.now()));
        entityManager.persist(expense("user-1", CategoryType.TRANSPORT, ExpenseType.EXPENSE,
                BigDecimal.valueOf(500), LocalDate.now()));
        entityManager.flush();

        Specification<Expense> spec = ExpenseSpecification.hasUserId("user-1")
                .and(ExpenseSpecification.hasCategory(CategoryType.FOOD))
                .and(ExpenseSpecification.minAmount(BigDecimal.valueOf(200)));

        var result = expenseRepository.findAll(spec, PageRequest.of(0, 10));

        // only the FOOD expense with amount >= 200 should match — not the FOOD one under
        // 200, and not the TRANSPORT one that satisfies amount but not category
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAmount()).isEqualTo(BigDecimal.valueOf(500));
    }

    @Test
    void nullFilterFields_areIgnored_actingAsNoOpPredicates() {
        // this is the important behavior: hasCategory(null), minAmount(null), etc. must
        // return `null` from the Specification lambda so Spring's `and()` skips them,
        // rather than generating "category = NULL" and matching nothing
        entityManager.persist(expense("user-1", CategoryType.FOOD, ExpenseType.EXPENSE,
                BigDecimal.valueOf(100), LocalDate.now()));
        entityManager.flush();

        Specification<Expense> spec = ExpenseSpecification.hasUserId("user-1")
                .and(ExpenseSpecification.hasCategory(null))
                .and(ExpenseSpecification.minAmount(null));

        var result = expenseRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void dateRangeFilters_excludeExpensesOutsideRange() {
        entityManager.persist(expense("user-1", CategoryType.FOOD, ExpenseType.EXPENSE,
                BigDecimal.TEN, LocalDate.of(2026, 1, 1)));
        entityManager.persist(expense("user-1", CategoryType.FOOD, ExpenseType.EXPENSE,
                BigDecimal.TEN, LocalDate.of(2026, 6, 15)));
        entityManager.flush();

        Specification<Expense> spec = ExpenseSpecification.hasUserId("user-1")
                .and(ExpenseSpecification.fromDate(LocalDate.of(2026, 5, 1)))
                .and(ExpenseSpecification.toDate(LocalDate.of(2026, 12, 31)));

        var result = expenseRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTransactionDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }
}