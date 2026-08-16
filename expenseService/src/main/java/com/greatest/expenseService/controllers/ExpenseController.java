package com.greatest.expenseService.controllers;

import com.greatest.expenseService.LogExecutionTime;
import com.greatest.expenseService.dto.*;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.ExpenseType;
import com.greatest.expenseService.services.BudgetService;
import com.greatest.expenseService.services.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("api/v1/expenses")
@RequiredArgsConstructor
@Slf4j
public class ExpenseController {

    private final ExpenseService expenseService;
    private final BudgetService budgetService;


    //    @GetMapping
//    public ResponseEntity<Page<ExpenseDto>> getExpenses(
//                @RequestHeader("X-User-Id") @NonNull String userId,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "10") int size,
//            @RequestParam(defaultValue = "createdAt") String sortBy,
//            @RequestParam(defaultValue = "false") boolean ascending
//    ) {
//        log.info("GET /api/v1/expenses - userId: {}, page: {}, size: {}", userId, page, size);
//
//        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
//        Pageable pageable = PageRequest.of(page, size, sort);
//
//        Page<ExpenseDto> expenses = expenseService.getExpenses(userId, pageable);
//
//        log.info("Retrieved {} expenses for userId: {}", expenses.getNumberOfElements(), userId);
//        return ResponseEntity.ok(expenses);
//    }
    @GetMapping
    public ResponseEntity<Page<ExpenseDto>>
    getExpenses(@AuthenticationPrincipal String userId,
                @RequestParam(required = false) CategoryType category,
                @RequestParam(required = false) ExpenseType type,
                @RequestParam(required = false) BigDecimal minAmount,
                @RequestParam(required = false) BigDecimal maxAmount,
                @RequestParam(required = false) LocalDate fromDate,
                @RequestParam(required = false) LocalDate toDate,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "10") int size,
                @RequestParam(defaultValue = "createdAt") String sortBy,
                @RequestParam(defaultValue = "false") boolean ascending) {
        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok(expenseService.searchExpenses(userId, category, type, minAmount, maxAmount, fromDate, toDate, pageable));
    }

    /**
     * Create a new expense
     */
    @Operation(
            summary = "Create Expense",
            description = "Creates a new expense transaction"
    )
    @PostMapping
    @LogExecutionTime
    public ResponseEntity<ExpenseDto> createExpense(@AuthenticationPrincipal String userId, @RequestBody @Valid ExpenseDto request) {
        log.info("POST /api/v1/expenses - userId: {}, externalId: {}", userId, request.getExternalId());

        request.setUserId(userId); // Ensure userId from header is used
        ExpenseDto created = expenseService.createExpense(request);

        log.info("Expense created successfully - id: {}", created.getExternalId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update an existing expense
     */
    @PutMapping
    public ResponseEntity<ExpenseDto> updateExpense(@AuthenticationPrincipal String userId, @RequestBody @Valid ExpenseDto request) {
        log.info("PUT /api/v1/expenses - userId: {}, externalId: {}", userId, request.getExternalId());

        request.setUserId(userId); // Ensure userId from header is used
        ExpenseDto updated = expenseService.updateExpense(request);

        log.info("Expense updated successfully - externalId: {}", updated.getExternalId());
        return ResponseEntity.ok(updated);
    }

    /**
     * get an existing expense
     */
    @GetMapping("/{externalId}")
    public ResponseEntity<ExpenseDto> getExpense(@AuthenticationPrincipal String userId, @PathVariable @NonNull String externalId) {
        log.info("get /api/v1/expenses/{} - userId: {}", externalId, userId);

        ExpenseDto dto = expenseService.getExpense(userId, externalId);

        log.info("Expense fetched successfully - externalId: {}", externalId);
        return ResponseEntity.ok(dto);
    }

    /**
     * Delete an expense by externalId
     */
    @DeleteMapping("/{externalId}")
    public ResponseEntity<Void> deleteExpense(@AuthenticationPrincipal String userId, @PathVariable @NonNull String externalId) {
        log.info("DELETE /api/v1/expenses/{} - userId: {}", externalId, userId);
        expenseService.deleteExpenseByExternalId(userId, externalId);
        log.info("Expense deleted successfully - externalId: {}", externalId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get financial summary for a user
     */
    @GetMapping("/summary")
    public ResponseEntity<Summary> getSummary(@AuthenticationPrincipal String userId) {
        log.info("GET /api/v1/expenses/summary - userId: {}", userId);

        Summary summary = expenseService.getSummary(userId);

        log.info("Summary retrieved - userId: {}, balance: {}", userId, summary.getBalance());
        return ResponseEntity.ok(summary);
    }


    @GetMapping("/analytics/category")
    public ResponseEntity<CategoryAnalyticsResponse> getCategoryAnalytics(@AuthenticationPrincipal String userId) {

        log.info("Getting analytics by category");
//        Thread.sleep(60000);
        return ResponseEntity.ok(expenseService.getCategoryAnalytics(userId));
    }

    @GetMapping("/analytics/monthly")
    public ResponseEntity<List<MonthlyAnalyticsDto>> getMonthlyAnalytics(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(expenseService.getMonthlyAnalytics(userId));
    }


    @PostMapping("/budgets")
    public ResponseEntity<BudgetDto> createBudget(@AuthenticationPrincipal String userId, @RequestBody BudgetDto dto) {
        return ResponseEntity.ok(budgetService.createBudget(userId, dto));
    }

    @GetMapping("/budgets/status")
    public ResponseEntity<List<BudgetStatusDto>> getBudgetStatus(
            @AuthenticationPrincipal String userId,
            @RequestParam int month,
            @RequestParam int year) {
        log.info("budget status");
        return ResponseEntity.ok(budgetService.getBudgetStatus(userId, month, year));
    }
}