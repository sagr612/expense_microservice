package com.greatest.expenseService.controllers;

import com.greatest.expenseService.dto.*;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.ExpenseType;
import com.greatest.expenseService.services.BudgetService;
import com.greatest.expenseService.services.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ExpenseController {

    private final ExpenseService expenseService;
    private final BudgetService budgetService;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "updatedAt", "transactionDate", "amount", "title", "currency", "category", "type");

    @GetMapping
    public ResponseEntity<Page<ExpenseDto>> getExpenses(Authentication authentication, @RequestParam(required = false) CategoryType category, @RequestParam(required = false) ExpenseType type, @RequestParam(required = false) BigDecimal minAmount, @RequestParam(required = false) BigDecimal maxAmount, @RequestParam(required = false) LocalDate fromDate, @RequestParam(required = false) LocalDate toDate, @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be greater than or equal to zero") int page, @RequestParam(defaultValue = "10") @Min(value = 1, message = "Size must be at least 1") @Max(value = 100, message = "Size must not exceed 100") int size,

                                                        @RequestParam(defaultValue = "createdAt") String sortBy, @RequestParam(defaultValue = "false") boolean ascending) {
        String userId = authentication.getName();
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("Invalid sort field. Allowed values: " + ALLOWED_SORT_FIELDS);
        }

        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);


        return ResponseEntity.ok(expenseService.searchExpenses(userId, category, type, minAmount, maxAmount, fromDate, toDate, pageable));
    }


    @Operation(summary = "Create Expense", description = "Creates a new expense transaction")
    @PostMapping
    public ResponseEntity<ExpenseDto> createExpense(Authentication authentication, @RequestBody @Valid ExpenseDto request) {
        String userId = authentication.getName();
        log.info("POST /api/v1/expenses - userId: {}, externalId: {}", userId, request.getExternalId());
        request.setUserId(userId);
        ExpenseDto created = expenseService.createExpense(request);

        log.info("Expense created successfully - id: {}", created.getExternalId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }


    @PutMapping
    public ResponseEntity<ExpenseDto> updateExpense(Authentication authentication, @RequestBody @Valid ExpenseDto request) {
        String userId = authentication.getName();
        log.info("PUT /api/v1/expenses - userId: {}, externalId: {}", userId, request.getExternalId());

        request.setUserId(userId);
        ExpenseDto updated = expenseService.updateExpense(request);

        log.info("Expense updated successfully - externalId: {}", updated.getExternalId());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{externalId}")
    public ResponseEntity<ExpenseDto> getExpense(Authentication authentication, @PathVariable @NonNull String externalId) {
        String userId = authentication.getName();
        log.info("get /api/v1/expenses/{} - userId: {}", externalId, userId);

        ExpenseDto dto = expenseService.getExpense(userId, externalId);

        log.info("Expense fetched successfully - externalId: {}", externalId);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{externalId}")
    public ResponseEntity<Void> deleteExpense(Authentication authentication, @PathVariable @NonNull String externalId) {
        String userId = authentication.getName();
        log.info("DELETE /api/v1/expenses/{} - userId: {}", externalId, userId);
        expenseService.deleteExpenseByExternalId(userId, externalId);
        log.info("Expense deleted successfully - externalId: {}", externalId);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/summary")
    public ResponseEntity<Summary> getSummary(Authentication authentication) {
        String userId = authentication.getName();
        log.info("GET /api/v1/expenses/summary - userId: {}", userId);

        Summary summary = expenseService.getSummary(userId);

        log.info("Summary retrieved - userId: {}, balance: {}", userId, summary.getBalance());
        return ResponseEntity.ok(summary);
    }


    @GetMapping("/analytics/category")
    public ResponseEntity<CategoryAnalyticsResponse> getCategoryAnalytics(Authentication authentication) {
        String userId = authentication.getName();
        log.info("Getting analytics by category");
        return ResponseEntity.ok(expenseService.getCategoryAnalytics(userId));
    }

    @GetMapping("/analytics/monthly")
    public ResponseEntity<List<MonthlyAnalyticsDto>> getMonthlyAnalytics(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(expenseService.getMonthlyAnalytics(userId));
    }


    @PostMapping("/budgets")
    public ResponseEntity<BudgetDto> createBudget(Authentication authentication, @RequestBody @Valid BudgetDto dto) {
        String userId = authentication.getName();

        return ResponseEntity.ok(budgetService.createBudget(userId, dto));
    }

    @GetMapping("/budgets/status")
    public ResponseEntity<List<BudgetStatusDto>> getBudgetStatus(
            Authentication authentication,
            @RequestParam @Min(value = 1, message = "Month must be between 1 and 12") @Max(value = 12, message = "Month must be between 1 and 12") int month,
            @RequestParam @Min(value = 2000, message = "Year must be >=2000") @Max(value = 9999, message = "Year must be <=9999") int year) {
        String userId = authentication.getName();
        log.info("budget status");
        return ResponseEntity.ok(budgetService.getBudgetStatus(userId, month, year));
    }
}