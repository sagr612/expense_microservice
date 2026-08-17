package com.greatest.expenseService.repositoires;

import com.greatest.expenseService.entities.Budget;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.repository.BudgetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")

class BudgetRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BudgetRepository budgetRepository;

    @Test
    void findByUserIdAndCategoryAndMonthAndYear_returnsBudget_whenExists() {
        Budget budget = Budget.builder()
                .userId("user-1").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(1000))
                .build();
        entityManager.persist(budget);
        entityManager.flush();

        Optional<Budget> result = budgetRepository
                .findByUserIdAndCategoryAndMonthAndYear("user-1", CategoryType.FOOD, 6, 2026);

        assertThat(result).isPresent();
    }

    @Test
    void enforcesUniqueConstraint_onUserCategoryMonthYear() {
        Budget b1 = Budget.builder()
                .userId("user-1").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(1000))
                .build();
        entityManager.persist(b1);
        entityManager.flush();

        Budget b2 = Budget.builder()
                .userId("user-1").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(500))
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            entityManager.persist(b2);
            entityManager.flush();
        });
    }

    @Test
    void findByUserIdAndMonthAndYear_returnsAllCategoriesForThatMonth() {
        entityManager.persist(Budget.builder().userId("user-1").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(1000)).build());
        entityManager.persist(Budget.builder().userId("user-1").category(CategoryType.TRANSPORT)
                .month(6).year(2026).amount(BigDecimal.valueOf(500)).build());
        entityManager.persist(Budget.builder().userId("user-1").category(CategoryType.FOOD)
                .month(7).year(2026).amount(BigDecimal.valueOf(1200)).build()); // different month
        entityManager.flush();

        List<Budget> result = budgetRepository.findByUserIdAndMonthAndYear("user-1", 6, 2026);

        assertThat(result).hasSize(2);
    }

    @Test
    void deleteByUserId_removesAllBudgetsForThatUser() {
        entityManager.persist(Budget.builder().userId("user-1").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(1000)).build());
        entityManager.persist(Budget.builder().userId("user-2").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(500)).build());
        entityManager.flush();

        budgetRepository.deleteByUserId("user-1");
        entityManager.flush();

        assertThat(budgetRepository.findByUserIdAndMonthAndYear("user-1", 6, 2026)).isEmpty();
        assertThat(budgetRepository.findByUserIdAndMonthAndYear("user-2", 6, 2026)).hasSize(1);
    }
}