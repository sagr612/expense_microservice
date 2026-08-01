package com.greatest.expenseService.services;

import com.greatest.expenseService.dto.*;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.Expense;
import com.greatest.expenseService.entities.ExpenseType;
import com.greatest.expenseService.producer.ExpenseCreatedProducer;
import com.greatest.expenseService.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private BudgetAlertService budgetAlertService;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private ExpenseCreatedProducer expenseCreatedProducer;

    @InjectMocks
    private ExpenseService expenseService;

    private ExpenseDto validDto() {
        return ExpenseDto.builder()
                .userId("user-1")
                .externalId("ext-1")
                .amount(BigDecimal.valueOf(100))
                .currency("INR")
                .type(ExpenseType.EXPENSE)
                .category(CategoryType.FOOD)
                .title("Lunch")
                .transactionDate(LocalDate.now())
                .build();
    }

    private Expense savedExpense(ExpenseDto dto) {
        return Expense.builder()
                .id("expense-id-1")
                .externalId(dto.getExternalId())
                .userId(dto.getUserId())
                .amount(dto.getAmount())
                .currency(dto.getCurrency())
                .type(dto.getType())
                .category(dto.getCategory())
                .title(dto.getTitle())
                .createdAt(java.time.Instant.now())
                .build();
    }

    @Nested
    class CreateExpense {

        @Test
        void createsExpense_andPublishesEvent_whenNotDuplicate() {
            ExpenseDto dto = validDto();
            when(expenseRepository.findByUserIdAndExternalId(dto.getUserId(), dto.getExternalId()))
                    .thenReturn(Optional.empty());
            when(expenseRepository.save(any(Expense.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ExpenseDto result = expenseService.createExpense(dto);

            assertThat(result.getUserId()).isEqualTo("user-1");
            assertThat(result.getAmount()).isEqualTo(BigDecimal.valueOf(100));

            verify(budgetAlertService).checkBudget(any(Expense.class));
            verify(expenseCreatedProducer).publish(any(ExpenseCreatedEvent.class));
        }

        @Test
        void returnsExistingExpense_withoutSavingOrPublishing_whenDuplicateExternalId() {
            ExpenseDto dto = validDto();
            Expense existing = savedExpense(dto);

            when(expenseRepository.findByUserIdAndExternalId(dto.getUserId(), dto.getExternalId()))
                    .thenReturn(Optional.of(existing));

            ExpenseDto result = expenseService.createExpense(dto);

            assertThat(result.getExternalId()).isEqualTo(dto.getExternalId());
            verify(expenseRepository, never()).save(any());
            verify(budgetAlertService, never()).checkBudget(any());
            verify(expenseCreatedProducer, never()).publish(any());
        }

        @Test
        void defaultsCurrencyToInr_whenCurrencyIsBlank() {
            ExpenseDto dto = validDto();
            dto.setCurrency(null);

            when(expenseRepository.findByUserIdAndExternalId(any(), any()))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
            when(expenseRepository.save(captor.capture()))
                    .thenAnswer(i -> i.getArgument(0));

            expenseService.createExpense(dto);

            assertThat(captor.getValue().getCurrency()).isEqualTo("INR");
        }

        @Test
        void generatesNewExternalId_ratherThanReusingRequestExternalId() {
            // ExpenseService.createExpense always generates a fresh UUID for the entity's
            // externalId rather than trusting the one on the incoming DTO
            ExpenseDto dto = validDto();

            when(expenseRepository.findByUserIdAndExternalId(any(), any()))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
            when(expenseRepository.save(captor.capture()))
                    .thenAnswer(i -> i.getArgument(0));

            expenseService.createExpense(dto);

            assertThat(captor.getValue().getExternalId()).isNotEqualTo(dto.getExternalId());
            assertThat(captor.getValue().getExternalId()).isNotBlank();
        }

        @Test
        void publishesEvent_withSavedExpenseDetails() {
            ExpenseDto dto = validDto();
            when(expenseRepository.findByUserIdAndExternalId(any(), any()))
                    .thenReturn(Optional.empty());
            when(expenseRepository.save(any(Expense.class)))
                    .thenAnswer(i -> i.getArgument(0));

            expenseService.createExpense(dto);

            ArgumentCaptor<ExpenseCreatedEvent> captor = ArgumentCaptor.forClass(ExpenseCreatedEvent.class);
            verify(expenseCreatedProducer).publish(captor.capture());

            assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
            assertThat(captor.getValue().getCategory()).isEqualTo("FOOD");
        }
    }

    @Nested
    class UpdateExpense {

        @Test
        void updatesOnlyProvidedFields_keepingExistingValuesForNullFields() {
            Instant now=Instant.now();
            Expense existing = Expense.builder()
                    .id("id-1").externalId("ext-1").userId("user-1")
                    .amount(BigDecimal.valueOf(50)).currency("INR")
                    .type(ExpenseType.EXPENSE).category(CategoryType.FOOD)
                    .title("Old title").note("old note").createdAt(now).updatedAt(now)
                    .build();

            ExpenseDto updateDto = ExpenseDto.builder()
                    .userId("user-1").externalId("ext-1")
                    .amount(BigDecimal.valueOf(75))
                    .title("New title")
                    // currency, type, category, note, transactionDate left null
                    .build();

            when(expenseRepository.findByUserIdAndExternalId("user-1", "ext-1"))
                    .thenReturn(Optional.of(existing));
            when(expenseRepository.save(any(Expense.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ExpenseDto result = expenseService.updateExpense(updateDto);

            assertThat(result.getAmount()).isEqualTo(BigDecimal.valueOf(75));
            assertThat(result.getTitle()).isEqualTo("New title");
            assertThat(existing.getCurrency()).isEqualTo("INR");   // unchanged
            assertThat(existing.getCategory()).isEqualTo(CategoryType.FOOD); // unchanged
            assertThat(existing.getNote()).isEqualTo("old note"); // unchanged
        }

        @Test
        void throwsIllegalArgument_whenExpenseNotFound() {
            ExpenseDto dto = validDto();
            when(expenseRepository.findByUserIdAndExternalId(dto.getUserId(), dto.getExternalId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> expenseService.updateExpense(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Expense not found");

            verify(expenseRepository, never()).save(any());
        }

        @Test
        void throwsIllegalArgument_whenUserIdBlank() {
            ExpenseDto dto = validDto();
            dto.setUserId("  ");

            assertThatThrownBy(() -> expenseService.updateExpense(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("UserId");
        }

        @Test
        void throwsIllegalArgument_whenExternalIdBlank() {
            ExpenseDto dto = validDto();
            dto.setExternalId(null);

            assertThatThrownBy(() -> expenseService.updateExpense(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ExternalId");
        }
        @Test
        void throwsIllegalArgument_whenNoFieldsToUpdate() {
            ExpenseDto dto = ExpenseDto.builder()
                    .userId("user-1")
                    .externalId("ext-1")
                    .build();
            Expense existing = Expense.builder()
                    .id("id-1").externalId("ext-1").build();
            when(expenseRepository.findByUserIdAndExternalId("user-1", "ext-1"))
                    .thenReturn( Optional.of(existing));
            assertThatThrownBy(() -> expenseService.updateExpense(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No fields provided");
        }


    }

    @Nested
    class DeleteExpense {

        @Test
        void deletesExpense_whenFound() {
            Expense existing = Expense.builder().id("id-1").userId("user-1").externalId("ext-1").build();
            when(expenseRepository.findByUserIdAndExternalId("user-1", "ext-1"))
                    .thenReturn(Optional.of(existing));

            expenseService.deleteExpenseByExternalId("user-1", "ext-1");

            verify(expenseRepository).delete(existing);
        }

        @Test
        void throwsIllegalArgument_whenExpenseNotFound() {
            when(expenseRepository.findByUserIdAndExternalId("user-1", "missing"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> expenseService.deleteExpenseByExternalId("user-1", "missing"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Expense not found");

            JpaRepository<Expense, String> repo=expenseRepository;
            verify(repo, never()).delete(any());
        }
    }

    @Nested
    class GetSummary {

        @Test
        void returnsNull_whenUserIdBlank() {
            assertThat(expenseService.getSummary(" ")).isNull();
            verifyNoInteractions(expenseRepository);
        }

        @Test
        void computesBalance_asIncomeMinusExpenditure() {
            SummaryProjection projection = new SummaryProjection(
                    BigDecimal.valueOf(1000), BigDecimal.valueOf(400), 10L, 3L, 7L);
            when(expenseRepository.getSummaryByUserId("user-1")).thenReturn(projection);

            Summary summary = expenseService.getSummary("user-1");

            assertThat(summary.getBalance()).isEqualTo(BigDecimal.valueOf(600));
            assertThat(summary.getTotalTransactions()).isEqualTo(10L);
        }
    }
}