package com.greatest.expenseService.services;

import com.greatest.expenseService.dto.BudgetDto;
import com.greatest.expenseService.dto.BudgetStatusDto;
import com.greatest.expenseService.entities.Budget;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.repository.BudgetRepository;
import com.greatest.expenseService.repository.ExpenseRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private BudgetService budgetService;

    @Nested
    class CreateBudget {


        @Test
        void createsNewBudget_whenNoneExistsForUserCategoryMonthYear() {
            BudgetDto dto = BudgetDto.builder()
                    .category(CategoryType.FOOD)
                    .amount(BigDecimal.valueOf(5000))
                    .month(6)
                    .year(2026)
                    .build();
            Budget newBudget = Budget.builder()
                    .id(1L).userId("user-1").category(CategoryType.FOOD)
                    .month(6).year(2026).amount(BigDecimal.valueOf(5000))
                    .build();

            when(budgetRepository.findByUserIdAndCategoryAndMonthAndYear(
                    "user-1", CategoryType.FOOD, 6, 2026))
                    .thenReturn(Optional.empty());

            when(budgetRepository.save(any())).thenReturn(newBudget);

            BudgetDto result = budgetService.createBudget("user-1", dto);


            assertThat(result.getAmount()).isEqualTo(BigDecimal.valueOf(5000));

            ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
            verify(budgetRepository).save(captor.capture());

            Budget saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo("user-1");
            assertThat(saved.getCategory()).isEqualTo(CategoryType.FOOD);
            assertThat(saved.getAmount()).isEqualTo(BigDecimal.valueOf(5000));
        }

        @Test
        void updatesExistingBudgetAmount_whenBudgetAlreadyExists() {
            Budget existing = Budget.builder()
                    .id(1L).userId("user-1").category(CategoryType.FOOD)
                    .month(6).year(2026).amount(BigDecimal.valueOf(3000))
                    .build();

            BudgetDto dto = BudgetDto.builder()
                    .category(CategoryType.FOOD)
                    .amount(BigDecimal.valueOf(8000))
                    .month(6).year(2026)
                    .build();

            when(budgetRepository.findByUserIdAndCategoryAndMonthAndYear(
                    "user-1", CategoryType.FOOD, 6, 2026))
                    .thenReturn(Optional.of(existing));

            when(budgetRepository.save(any())).thenReturn(existing);
            budgetService.createBudget("user-1", dto);

            ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
            verify(budgetRepository).save(captor.capture());

            // must update the SAME row, not create a duplicate
            assertThat(captor.getValue().getId()).isEqualTo(1L);
            assertThat(captor.getValue().getAmount()).isEqualTo(BigDecimal.valueOf(8000));
        }
    }

    @Nested
    class GetBudgetStatus {

        @Test
        void computesRemainingAndPercentage_forEachBudget() {
            Budget budget = Budget.builder()
                    .id(1L).userId("user-1").category(CategoryType.FOOD)
                    .month(6).year(2026).amount(BigDecimal.valueOf(1000))
                    .build();

            when(budgetRepository.findByUserIdAndMonthAndYear("user-1", 6, 2026))
                    .thenReturn(List.of(budget));
            when(expenseRepository.getSpentAmount("user-1", CategoryType.FOOD, 6, 2026))
                    .thenReturn(BigDecimal.valueOf(250));

            List<BudgetStatusDto> result = budgetService.getBudgetStatus("user-1", 6, 2026);

            assertThat(result).hasSize(1);
            BudgetStatusDto status = result.get(0);
            assertThat(status.getBudget()).isEqualTo(BigDecimal.valueOf(1000));
            assertThat(status.getSpent()).isEqualTo(BigDecimal.valueOf(250));
            assertThat(status.getRemaining()).isEqualTo(BigDecimal.valueOf(750));
            assertThat(status.getPercentageUsed()).isEqualTo(25.0);
        }

        @Test
        void returnsEmptyList_whenNoBudgetsForMonth() {
            when(budgetRepository.findByUserIdAndMonthAndYear("user-1", 6, 2026))
                    .thenReturn(List.of());

            List<BudgetStatusDto> result = budgetService.getBudgetStatus("user-1", 6, 2026);

            assertThat(result).isEmpty();
            verifyNoInteractions(expenseRepository);
        }

        @Test
        void handlesMultipleBudgets_independently() {
            Budget food = Budget.builder().userId("user-1").category(CategoryType.FOOD)
                    .month(6).year(2026).amount(BigDecimal.valueOf(1000)).build();
            Budget transport = Budget.builder().userId("user-1").category(CategoryType.TRANSPORT)
                    .month(6).year(2026).amount(BigDecimal.valueOf(500)).build();

            when(budgetRepository.findByUserIdAndMonthAndYear("user-1", 6, 2026))
                    .thenReturn(List.of(food, transport));
            when(expenseRepository.getSpentAmount("user-1", CategoryType.FOOD, 6, 2026))
                    .thenReturn(BigDecimal.valueOf(100));
            when(expenseRepository.getSpentAmount("user-1", CategoryType.TRANSPORT, 6, 2026))
                    .thenReturn(BigDecimal.valueOf(500));

            List<BudgetStatusDto> result = budgetService.getBudgetStatus("user-1", 6, 2026);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(BudgetStatusDto::getCategory)
                    .containsExactlyInAnyOrder(CategoryType.FOOD, CategoryType.TRANSPORT);
        }
    }
}