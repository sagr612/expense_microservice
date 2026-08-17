package com.greatest.expenseService.repositoires;

import com.greatest.expenseService.dto.SummaryProjection;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.Expense;
import com.greatest.expenseService.entities.ExpenseType;
import com.greatest.expenseService.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ExpenseRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ExpenseRepository expenseRepository;

    private Expense expense(String userId, String externalId, BigDecimal amount, CategoryType category, ExpenseType type, LocalDate date) {
        return Expense.builder()
                .userId(userId)
                .externalId(externalId)
                .amount(amount)
                .currency("INR")
                .category(category)
                .type(type)
                .title("t")
                .transactionDate(date)
                .build();
    }

    @Test
    void findByUserIdAndExternalId_returnsExpense_whenExists() {
        Expense e = expense("user-1", "ext-1", BigDecimal.TEN, CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.now());
        entityManager.persist(e);
        entityManager.flush();

        Optional<Expense> result = expenseRepository.findByUserIdAndExternalId("user-1", "ext-1");

        assertThat(result).isPresent();
        assertThat(result.get().getAmount()).isEqualTo(BigDecimal.TEN);
    }

    @Test
    void findByUserIdAndExternalId_returnsEmpty_whenNotFound() {
        Optional<Expense> result = expenseRepository.findByUserIdAndExternalId("user-1", "missing");
        assertThat(result).isEmpty();
    }

    @Test
    void enforcesUniqueConstraint_onUserIdAndExternalId() {
        Expense e1 = expense("user-1", "ext-dup", BigDecimal.TEN, CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.now());
        entityManager.persist(e1);
        entityManager.flush();

        Expense e2 = expense("user-1", "ext-dup", BigDecimal.ONE, CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.now());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            entityManager.persist(e2);
            entityManager.flush();
        });
    }

    @Test
    void findByUserId_returnsPagedResults() {
        entityManager.persist(expense("user-1", "e1", BigDecimal.TEN, CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.now()));
        entityManager.persist(expense("user-1", "e2", BigDecimal.ONE, CategoryType.TRANSPORT, ExpenseType.EXPENSE, LocalDate.now()));
        entityManager.persist(expense("user-2", "e3", BigDecimal.ONE, CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.now()));
        entityManager.flush();

        var page = expenseRepository.findByUserId("user-1", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void countByUserId_countsOnlyThatUsersExpenses() {
        entityManager.persist(expense("user-1", "e1", BigDecimal.TEN, CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.now()));
        entityManager.persist(expense("user-2", "e2", BigDecimal.ONE, CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.now()));
        entityManager.flush();

        assertThat(expenseRepository.countByUserId("user-1")).isEqualTo(1L);
    }

    @Test
    void getSpentAmount_sumsOnlyExpenseType_forMatchingCategoryMonthYear() {
        entityManager.persist(expense("user-1", "e1", BigDecimal.valueOf(100),
                CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.of(2026, 6, 5)));
        entityManager.persist(expense("user-1", "e2", BigDecimal.valueOf(50),
                CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.of(2026, 6, 20)));
        // different category — should not count
        entityManager.persist(expense("user-1", "e3", BigDecimal.valueOf(999),
                CategoryType.TRANSPORT, ExpenseType.EXPENSE, LocalDate.of(2026, 6, 10)));
        // income — should not count
        entityManager.persist(expense("user-1", "e4", BigDecimal.valueOf(999),
                CategoryType.FOOD, ExpenseType.INCOME, LocalDate.of(2026, 6, 10)));
        entityManager.flush();

        BigDecimal spent = expenseRepository.getSpentAmount("user-1", CategoryType.FOOD, 6, 2026);

        assertThat(spent).isEqualByComparingTo(BigDecimal.valueOf(150));
    }

    @Test
    void deleteByUserId_removesAllExpensesForThatUser() {
        entityManager.persist(expense("user-1", "e1", BigDecimal.TEN, CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.now()));
        entityManager.persist(expense("user-2", "e2", BigDecimal.ONE, CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.now()));
        entityManager.flush();

        expenseRepository.deleteByUserId("user-1");

        assertThat(expenseRepository.countByUserId("user-1")).isZero();
        assertThat(expenseRepository.countByUserId("user-2")).isEqualTo(1L);
    }

    @Test
    void getSummaryByUserId_computesIncomeAndExpenditure_correctly() {
        entityManager.persist(expense("user-1", "e1", BigDecimal.valueOf(500),
                CategoryType.FOOD, ExpenseType.INCOME, LocalDate.now()));
        entityManager.persist(expense("user-1", "e2", BigDecimal.valueOf(200),
                CategoryType.FOOD, ExpenseType.EXPENSE, LocalDate.now()));
        entityManager.flush();

        SummaryProjection p = expenseRepository.getSummaryByUserId("user-1");

        assertThat(p.getIncome()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(p.getExpenditure()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(p.getTotalTransactions()).isEqualTo(2L);
    }

}