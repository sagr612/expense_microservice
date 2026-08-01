package com.greatest.expenseService.services;

import com.greatest.expenseService.dto.BudgetAlertEvent;
import com.greatest.expenseService.entities.Budget;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.Expense;
import com.greatest.expenseService.entities.ExpenseType;
import com.greatest.expenseService.producer.BudgetAlertProducer;
import com.greatest.expenseService.repository.BudgetRepository;
import com.greatest.expenseService.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetAlertServiceTest {

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private BudgetAlertProducer producer;

    @InjectMocks
    private BudgetAlertService budgetAlertService;

    private Expense expenseOf(BigDecimal amount, CategoryType category, LocalDate date) {
        return Expense.builder()
                .userId("user-1")
                .amount(amount)
                .category(category)
                .type(ExpenseType.EXPENSE)
                .transactionDate(date)
                .build();
    }

    @Test
    void doesNothing_whenNoBudgetExistsForCategory() {
        Expense expense = expenseOf(BigDecimal.valueOf(100), CategoryType.FOOD, LocalDate.of(2026, 6, 15));

        when(budgetRepository.findByUserIdAndCategoryAndMonthAndYear(
                "user-1", CategoryType.FOOD, 6, 2026))
                .thenReturn(Optional.empty());

        budgetAlertService.checkBudget(expense);

        verifyNoInteractions(producer);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void triggersAlert_atFiftyPercentThreshold() {
        Expense expense = expenseOf(BigDecimal.valueOf(50), CategoryType.FOOD, LocalDate.of(2026, 6, 15));
        Budget budget = Budget.builder()
                .userId("user-1").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(100))
                .lastAlertThreshold(null)
                .build();

        when(budgetRepository.findByUserIdAndCategoryAndMonthAndYear(
                "user-1", CategoryType.FOOD, 6, 2026))
                .thenReturn(Optional.of(budget));
        when(expenseRepository.getSpentAmount("user-1", CategoryType.FOOD, 6, 2026))
                .thenReturn(BigDecimal.valueOf(50));

        budgetAlertService.checkBudget(expense);

        ArgumentCaptor<BudgetAlertEvent> captor = ArgumentCaptor.forClass(BudgetAlertEvent.class);
        verify(producer).publish(captor.capture());
        assertThat(captor.getValue().getAlertType()).isEqualTo("50_PERCENT");
        assertThat(budget.getLastAlertThreshold()).isEqualTo(50);
        verify(budgetRepository).save(budget);
    }

    @Test
    void triggersAlert_atEightyPercentThreshold() {
        Expense expense = expenseOf(BigDecimal.valueOf(80), CategoryType.FOOD, LocalDate.of(2026, 6, 15));
        Budget budget = Budget.builder()
                .userId("user-1").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(100))
                .lastAlertThreshold(50)
                .build();

        when(budgetRepository.findByUserIdAndCategoryAndMonthAndYear(any(), any(), anyInt(), anyInt()))
                .thenReturn(Optional.of(budget));
        when(expenseRepository.getSpentAmount(any(), any(), anyInt(), anyInt()))
                .thenReturn(BigDecimal.valueOf(80));

        budgetAlertService.checkBudget(expense);

        ArgumentCaptor<BudgetAlertEvent> captor = ArgumentCaptor.forClass(BudgetAlertEvent.class);
        verify(producer).publish(captor.capture());
        assertThat(captor.getValue().getAlertType()).isEqualTo("80_PERCENT");
    }

    @Test
    void triggersAlert_atHundredPercentThreshold_whenOverBudget() {
        Expense expense = expenseOf(BigDecimal.valueOf(150), CategoryType.FOOD, LocalDate.of(2026, 6, 15));
        Budget budget = Budget.builder()
                .userId("user-1").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(100))
                .lastAlertThreshold(80)
                .build();

        when(budgetRepository.findByUserIdAndCategoryAndMonthAndYear(any(), any(), anyInt(), anyInt()))
                .thenReturn(Optional.of(budget));
        when(expenseRepository.getSpentAmount(any(), any(), anyInt(), anyInt()))
                .thenReturn(BigDecimal.valueOf(150));

        budgetAlertService.checkBudget(expense);

        ArgumentCaptor<BudgetAlertEvent> captor = ArgumentCaptor.forClass(BudgetAlertEvent.class);
        verify(producer).publish(captor.capture());
        assertThat(captor.getValue().getAlertType()).isEqualTo("100_PERCENT");
    }

    @Test
    void doesNotRepublishAlert_whenThresholdUnchangedSinceLastAlert() {
        // regression test: without this de-dupe check, every single expense in the same
        // bracket would refire the same alert and spam the user
        Expense expense = expenseOf(BigDecimal.valueOf(55), CategoryType.FOOD, LocalDate.of(2026, 6, 15));
        Budget budget = Budget.builder()
                .userId("user-1").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(100))
                .lastAlertThreshold(50)
                .build();

        when(budgetRepository.findByUserIdAndCategoryAndMonthAndYear(any(), any(), anyInt(), anyInt()))
                .thenReturn(Optional.of(budget));
        when(expenseRepository.getSpentAmount(any(), any(), anyInt(), anyInt()))
                .thenReturn(BigDecimal.valueOf(55)); // still in the 50% bracket

        budgetAlertService.checkBudget(expense);

        verifyNoInteractions(producer);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void doesNotTriggerAlert_whenSpendingBelowFiftyPercent() {
        Expense expense = expenseOf(BigDecimal.valueOf(20), CategoryType.FOOD, LocalDate.of(2026, 6, 15));
        Budget budget = Budget.builder()
                .userId("user-1").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(100))
                .lastAlertThreshold(null)
                .build();

        when(budgetRepository.findByUserIdAndCategoryAndMonthAndYear(any(), any(), anyInt(), anyInt()))
                .thenReturn(Optional.of(budget));
        when(expenseRepository.getSpentAmount(any(), any(), anyInt(), anyInt()))
                .thenReturn(BigDecimal.valueOf(20));

        budgetAlertService.checkBudget(expense);

        verifyNoInteractions(producer);
    }

    @Test
    void treatsNullSpentAmount_asZero() {
        // getSpentAmount can theoretically return null (COALESCE not applied in some path);
        // must not NPE
        Expense expense = expenseOf(BigDecimal.valueOf(10), CategoryType.FOOD, LocalDate.of(2026, 6, 15));
        Budget budget = Budget.builder()
                .userId("user-1").category(CategoryType.FOOD)
                .month(6).year(2026).amount(BigDecimal.valueOf(100))
                .build();

        when(budgetRepository.findByUserIdAndCategoryAndMonthAndYear(any(), any(), anyInt(), anyInt()))
                .thenReturn(Optional.of(budget));
        when(expenseRepository.getSpentAmount(any(), any(), anyInt(), anyInt()))
                .thenReturn(null);

        budgetAlertService.checkBudget(expense);

        verifyNoInteractions(producer);
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}