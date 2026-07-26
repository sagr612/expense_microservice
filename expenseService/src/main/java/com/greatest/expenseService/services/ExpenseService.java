package com.greatest.expenseService.services;

import com.greatest.expenseService.LogExecutionTime;
import com.greatest.expenseService.dto.*;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.Expense;
import com.greatest.expenseService.entities.ExpenseType;
import com.greatest.expenseService.producer.ExpenseCreatedProducer;
import com.greatest.expenseService.repository.ExpenseRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {
    private final BudgetAlertService budgetAlertService;
    private final ExpenseRepository expenseRepository;
    private final ExpenseCreatedProducer expenseCreatedProducer;

    private ExpenseDto convert(Expense e) {
        return ExpenseDto.builder()
                .externalId(e.getExternalId())
                .userId(e.getUserId())
                .currency(e.getCurrency())
                .createdAt(e.getCreatedAt().toString())
                .amount(e.getAmount())
                .category(e.getCategory())
                .type(e.getType())
                .title(e.getTitle())
                .note(e.getNote())
                .version(e.getVersion())
                .build();
    }

    @LogExecutionTime
    public Page<ExpenseDto> getExpenses(@NonNull String userId, Pageable pageable) {
        log.debug("Fetching expenses for userId: {}, page: {}", userId, pageable.getPageNumber());

        Page<Expense> expenses = expenseRepository.findByUserId(userId, pageable);
        return expenses.map(this::convert);
    }

    @Transactional
    public ExpenseDto createExpense(ExpenseDto req) {
        log.info("Creating expense for userId: {}, externalId: {}", req.getUserId(), req.getExternalId());

        Optional<Expense> expense_exists = expenseRepository.findByUserIdAndExternalId(req.getUserId(), req.getExternalId());
        if (expense_exists.isPresent()) {
            return convert(expense_exists.get());
        }

        String ext = UUID.randomUUID().toString();
        Expense expense = Expense.builder()
                .externalId(ext)
                .createdAt(Instant.now())
                .amount(req.getAmount())
                .currency(Strings.isNotBlank(req.getCurrency()) ? req.getCurrency() : "INR")
                .userId(req.getUserId())
                .category(req.getCategory())
                .type(req.getType())
                .title(req.getTitle())
                .note(req.getNote())
                .transactionDate(req.getTransactionDate())
                .build();

        Expense saved = expenseRepository.save(expense);
        log.info("Expense created - id: {}", saved.getId());
        budgetAlertService.checkBudget(saved);
        ExpenseCreatedEvent event =
                ExpenseCreatedEvent.builder()
                        .userId(saved.getUserId())
                        .amount(saved.getAmount())
                        .category(saved.getCategory().name())
                        .externalId(saved.getExternalId())
                        .build();

        expenseCreatedProducer.publish(event);

        return convert(saved);

    }

    @Transactional
    public ExpenseDto updateExpense(ExpenseDto req) {
        log.info("Updating expense - userId: {}, externalId: {}", req.getUserId(), req.getExternalId());

        validateExpenseDto(req);

        Expense expense = expenseRepository
                .findByUserIdAndExternalId(req.getUserId(), req.getExternalId())
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));

        if (req.getAmount() != null) {
            expense.setAmount(req.getAmount());
        }

        if (Strings.isNotBlank(req.getCurrency())) {
            expense.setCurrency(req.getCurrency());
        }
        if (req.getType() != null) {          // ← enums use null check, not isBlank
            expense.setType(req.getType());
        }
        if (req.getCategory() != null) {      // ← same here
            expense.setCategory(req.getCategory());
        }
        if (Strings.isNotBlank(req.getTitle())) {
            expense.setTitle(req.getTitle());
        }

        if (Strings.isNotBlank(req.getNote())) {
            expense.setNote(req.getNote());
        }
        if(req.getTransactionDate()!=null){
            expense.setTransactionDate(req.getTransactionDate());
        }
        Expense updated = expenseRepository.save(expense);
        log.info("Expense updated - id: {}", updated.getId());

        return convert(updated);
    }


    @Transactional
    public void deleteExpenseByExternalId(@NonNull String userId, @NonNull String externalId) {
        log.info("Deleting expense - userId: {}, externalId: {}", userId, externalId);

        Expense expense = expenseRepository
                .findByUserIdAndExternalId(userId, externalId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));

        expenseRepository.delete(expense);
        log.info("Expense deleted - id: {}", expense.getId());
    }

    public Summary getSummary(@NonNull String userId) {
        if (userId.isBlank()) {
            log.error("User id is blank");
            return null;

        }

        log.debug("Calculating summary for userId: {}", userId);
        SummaryProjection p = expenseRepository.getSummaryByUserId(userId);

        return Summary.builder()
                .income(p.getIncome())
                .expenditure(p.getExpenditure())
                .balance(p.getIncome().subtract(p.getExpenditure()))
                .totalTransactions(p.getTotalTransactions())
                .incomeTransactions(p.getIncomeTransactions())
                .expenditureTransactions(p.getExpenditureTransactions())
                .build();


    }

    private void validateExpenseDto(ExpenseDto dto) {
        if (Strings.isBlank(dto.getUserId())) {
            throw new IllegalArgumentException("UserId cannot be blank");
        }
        if (Strings.isBlank(dto.getExternalId())) {
            throw new IllegalArgumentException("ExternalId cannot be blank");
        }
        if (dto.getAmount() == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        if (dto.getType() == null) {                          // ← add this
            throw new IllegalArgumentException("Type cannot be null");
        }
        if (dto.getCategory() == null) {                      // ← add this
            throw new IllegalArgumentException("Category cannot be null");
        }

        if (dto.getTitle() == null) {
            throw new IllegalArgumentException("Title cannot be null");
        }

    }

    public ExpenseDto getExpense(@NonNull String userId, @NonNull String externalId) {
        Expense expense = expenseRepository.findByUserIdAndExternalId(userId, externalId).orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        return convert(expense);
    }


    public CategoryAnalyticsResponse
    getCategoryAnalytics(String userId) {

        List<CategoryAnalyticsDto> result =
                expenseRepository.getCategoryAnalytics(
                        userId,
                        ExpenseType.EXPENSE
                );

        Map<CategoryType, BigDecimal> analytics =
                result.stream()
                        .collect(Collectors.toMap(
                                CategoryAnalyticsDto::getCategory,
                                CategoryAnalyticsDto::getTotalAmount
                        ));

        return new CategoryAnalyticsResponse(
                analytics
        );
    }

    public List<MonthlyAnalyticsDto>
    getMonthlyAnalytics(String userId) {
        return expenseRepository
                .getMonthlyAnalytics(
                        userId,
                        ExpenseType.EXPENSE
                )
                .stream()
                .map(row ->
                        new MonthlyAnalyticsDto(
                                ((Number) row[0]).intValue(),
                                ((Number) row[1]).intValue(),
                                (BigDecimal) row[2]
                        ))
                .toList();
    }

    public Page<ExpenseDto> searchExpenses(
            String userId,
            CategoryType category,
            ExpenseType type,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {

        Specification<Expense> spec = Specification
                .where(ExpenseSpecification.hasUserId(userId))
                .and(ExpenseSpecification.hasCategory(category))
                .and(ExpenseSpecification.hasType(type))
                .and(ExpenseSpecification.minAmount(minAmount))
                .and(ExpenseSpecification.maxAmount(maxAmount))
                .and(ExpenseSpecification.fromDate(fromDate))
                .and(ExpenseSpecification.toDate(toDate));

        return expenseRepository
                .findAll(spec, pageable)
                .map(this::convert);
    }
}
