package com.greatest.expenseService.services;

import com.greatest.expenseService.dto.*;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.Expense;
import com.greatest.expenseService.entities.ExpenseType;
import com.greatest.expenseService.repository.ExpenseRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceAdditionalTest {

    @Mock
    private BudgetAlertService budgetAlertService;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private com.greatest.expenseService.repository.OutboxRepository outboxRepository;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private ExpenseService expenseService;

    private Expense expense(String externalId, BigDecimal amount, CategoryType category) {
        return Expense.builder()
                .id("id-" + externalId)
                .externalId(externalId)
                .userId("user-1")
                .amount(amount)
                .currency("INR")
                .type(ExpenseType.EXPENSE)
                .category(category)
                .title("t")
                .createdAt(Instant.now())
                .build();
    }

    //=========================================================
    // getExpenses() — pagination mapping
    //=========================================================

    @Nested
    class GetExpenses {

        @Test
        void mapsPageOfEntities_toPageOfDtos_preservingPageMetadata() {
            Expense e1 = expense("ext-1", BigDecimal.valueOf(100), CategoryType.FOOD);
            Expense e2 = expense("ext-2", BigDecimal.valueOf(200), CategoryType.TRANSPORT);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Expense> entityPage = new PageImpl<>(List.of(e1, e2), pageable, 2);

            when(expenseRepository.findByUserId("user-1", pageable)).thenReturn(entityPage);

            Page<ExpenseDto> result = expenseService.getExpenses("user-1", pageable);

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).extracting(ExpenseDto::getExternalId)
                    .containsExactly("ext-1", "ext-2");
            assertThat(result.getContent().get(0).getAmount()).isEqualTo(BigDecimal.valueOf(100));
        }

        @Test
        void returnsEmptyPage_whenUserHasNoExpenses() {
            Pageable pageable = PageRequest.of(0, 10);
            when(expenseRepository.findByUserId("user-1", pageable))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            Page<ExpenseDto> result = expenseService.getExpenses("user-1", pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        void throwsNullPointerException_whenUserIdIsNull() {
            // getExpenses is annotated @NonNull on userId — Lombok/Spring's null-check
            // should fail fast rather than silently querying with a null userId
            assertThatThrownBy(() -> expenseService.getExpenses(null, PageRequest.of(0, 10)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    //=========================================================
    // getExpense() — found / not-found paths
    //=========================================================

    @Nested
    class GetExpense {

        @Test
        void returnsMappedDto_whenExpenseExists() {
            Expense e = expense("ext-1", BigDecimal.valueOf(100), CategoryType.FOOD);
            when(expenseRepository.findByUserIdAndExternalId("user-1", "ext-1"))
                    .thenReturn(Optional.of(e));

            ExpenseDto result = expenseService.getExpense("user-1", "ext-1");

            assertThat(result.getExternalId()).isEqualTo("ext-1");
            assertThat(result.getUserId()).isEqualTo("user-1");
        }

        @Test
        void throwsIllegalArgument_whenExpenseNotFound() {
            when(expenseRepository.findByUserIdAndExternalId("user-1", "missing"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> expenseService.getExpense("user-1", "missing"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Expense not found");
        }
    }

    //=========================================================
    // searchExpenses() — Specification wiring
    //=========================================================

    @Nested
    class SearchExpenses {

        @Test
        void passesSpecificationAndPageable_toRepository_andMapsResults() {
            Expense e = expense("ext-1", BigDecimal.valueOf(300), CategoryType.FOOD);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Expense> page = new PageImpl<>(List.of(e), pageable, 1);

            // searchExpenses builds its own Specification internally, so we can't predict
            // its exact identity — just verify a Specification (any) + our exact Pageable
            // reach the repository, and that the entity->dto mapping happens
            when(expenseRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);

            Page<ExpenseDto> result = expenseService.searchExpenses(
                    "user-1", CategoryType.FOOD, ExpenseType.EXPENSE,
                    BigDecimal.valueOf(100), BigDecimal.valueOf(1000),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getExternalId()).isEqualTo("ext-1");

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(expenseRepository).findAll(any(Specification.class), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue()).isEqualTo(pageable);
        }

        @Test
        void worksWithAllOptionalFiltersNull_stillCallingRepositoryWithSpecification() {
            // exercises the path where category/type/minAmount/maxAmount/dates are all
            // null — ExpenseSpecification's null-safe predicates must let this through
            // without the service throwing before it even reaches the repository
            Pageable pageable = PageRequest.of(0, 10);
            when(expenseRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            Page<ExpenseDto> result = expenseService.searchExpenses(
                    "user-1", null, null, null, null, null, null, pageable);

            assertThat(result.getContent()).isEmpty();
            verify(expenseRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void appliesUserIdFilter_regardlessOfOtherFilters() {
            // can't inspect the Specification's predicate tree directly without a real
            // EntityManager, but we can at least verify searchExpenses always calls
            // through with *some* specification rather than skipping filtering entirely
            Pageable pageable = PageRequest.of(0, 10);
            when(expenseRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            expenseService.searchExpenses(
                    "user-1", CategoryType.FOOD, null, null, null, null, null, pageable);

            ArgumentCaptor<Specification<Expense>> specCaptor = ArgumentCaptor.forClass(Specification.class);
            verify(expenseRepository).findAll(specCaptor.capture(), any(Pageable.class));
            assertThat(specCaptor.getValue()).isNotNull();
        }
    }

    //=========================================================
    // getCategoryAnalytics() — mapping + empty-result path
    //=========================================================

    @Nested
    class GetCategoryAnalytics {

        @Test
        void mapsCategoryAnalyticsDtoList_toResponseMap() {
            List<CategoryAnalyticsDto> rows = List.of(
                    new CategoryAnalyticsDto(CategoryType.FOOD, BigDecimal.valueOf(500)),
                    new CategoryAnalyticsDto(CategoryType.TRANSPORT, BigDecimal.valueOf(200))
            );
            when(expenseRepository.getCategoryAnalytics("user-1", ExpenseType.EXPENSE))
                    .thenReturn(rows);

            CategoryAnalyticsResponse response = expenseService.getCategoryAnalytics("user-1");

            assertThat(response.getAnalytics())
                    .containsEntry(CategoryType.FOOD, BigDecimal.valueOf(500))
                    .containsEntry(CategoryType.TRANSPORT, BigDecimal.valueOf(200));
        }

        @Test
        void returnsEmptyMap_whenNoExpensesExist() {
            when(expenseRepository.getCategoryAnalytics("user-1", ExpenseType.EXPENSE))
                    .thenReturn(List.of());

            CategoryAnalyticsResponse response = expenseService.getCategoryAnalytics("user-1");

            assertThat(response.getAnalytics()).isEmpty();
        }

        @Test
        void alwaysQueriesWithExpenseType_neverIncome() {
            // category analytics is explicitly EXPENSE-only per the current implementation —
            // this locks that decision in so nobody accidentally widens it without noticing
            expenseService.getCategoryAnalytics("user-1");

            verify(expenseRepository).getCategoryAnalytics("user-1", ExpenseType.EXPENSE);
            verify(expenseRepository, never()).getCategoryAnalytics("user-1", ExpenseType.INCOME);
        }
    }

    //=========================================================
    // getMonthlyAnalytics() — Object[] row mapping
    //=========================================================

    @Nested
    class GetMonthlyAnalytics {

        @Test
        void mapsObjectArrayRows_toMonthlyAnalyticsDtoList() {
            // the repository returns raw Object[] rows: [year, month, sum] — Number types
            // can vary (Integer/Long/BigInteger depending on DB driver), so the service
            // must defensively call .intValue() rather than a direct cast
            List<Object[]> rows = List.of(
                    new Object[]{2026, 6, BigDecimal.valueOf(750)},
                    new Object[]{2026, 7, BigDecimal.valueOf(1200)}
            );
            when(expenseRepository.getMonthlyAnalytics("user-1", ExpenseType.EXPENSE))
                    .thenReturn(rows);

            List<MonthlyAnalyticsDto> result = expenseService.getMonthlyAnalytics("user-1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getYear()).isEqualTo(2026);
            assertThat(result.get(0).getMonth()).isEqualTo(6);
            assertThat(result.get(0).getTotalAmount()).isEqualTo(BigDecimal.valueOf(750));
            assertThat(result.get(1).getMonth()).isEqualTo(7);
        }

        @Test
        void mapsCorrectly_whenYearAndMonthAreLongInsteadOfInteger() {
            // regression guard: some JDBC drivers return EXTRACT(...) results as Long,
            // not Integer — .intValue() on Number handles both, a hard cast to Integer
            // would throw ClassCastException here
            List<Object[]> rows = List.of(
                    new Object[]{2026L, 6L, BigDecimal.valueOf(300)},
                    new Object[]{2026L, 5L, BigDecimal.valueOf(300)}

            );
            when(expenseRepository.getMonthlyAnalytics("user-1", ExpenseType.EXPENSE))
                    .thenReturn(rows);

            List<MonthlyAnalyticsDto> result = expenseService.getMonthlyAnalytics("user-1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getYear()).isEqualTo(2026);
            assertThat(result.get(0).getMonth()).isEqualTo(6);
        }

        @Test
        void returnsEmptyList_whenNoExpensesExist() {
            when(expenseRepository.getMonthlyAnalytics("user-1", ExpenseType.EXPENSE))
                    .thenReturn(List.of());

            List<MonthlyAnalyticsDto> result = expenseService.getMonthlyAnalytics("user-1");

            assertThat(result).isEmpty();
        }

        @Test
        void alwaysQueriesWithExpenseType_neverIncome() {
            expenseService.getMonthlyAnalytics("user-1");

            verify(expenseRepository).getMonthlyAnalytics("user-1", ExpenseType.EXPENSE);
            verify(expenseRepository, never()).getMonthlyAnalytics("user-1", ExpenseType.INCOME);
        }
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}