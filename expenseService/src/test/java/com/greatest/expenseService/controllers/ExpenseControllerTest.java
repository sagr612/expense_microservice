package com.greatest.expenseService.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.expenseService.dto.*;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.ExpenseType;
import com.greatest.expenseService.services.BudgetService;
import com.greatest.expenseService.services.ExpenseService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ExpenseService expenseService;

    @MockitoBean
    BudgetService budgetService;

    @MockitoBean
    Tracer tracer;

    private ExpenseDto validDto() {
        return ExpenseDto.builder()
                .userId("user-1")
                .externalId("ext-1")
                .amount(BigDecimal.valueOf(100))
                .currency("INR")
                .type(ExpenseType.EXPENSE)
                .category(CategoryType.FOOD)
                .title("Lunch")
                .build();
    }

    //---------------------------------------------------------
    // createExpense
    //---------------------------------------------------------

    @Test
    void shouldCreateExpenseSuccessfully() throws Exception {
        ExpenseDto dto = validDto();
        when(expenseService.createExpense(any(ExpenseDto.class))).thenReturn(dto);

        mockMvc.perform(post("/api/v1/expenses")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user-1"));

        verify(expenseService).createExpense(any(ExpenseDto.class));
    }

    @Test
    void shouldReturnBadRequest_whenAmountMissing() throws Exception {
        ExpenseDto dto = validDto();
        dto.setAmount(null);

        mockMvc.perform(post("/api/v1/expenses")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    @Test
    void shouldOverrideRequestUserId_withHeaderUserId() throws Exception {
        // controller must force userId from X-User-Id header, not trust the body
        ExpenseDto dto = validDto();
        dto.setUserId("someone-else");
        when(expenseService.createExpense(any(ExpenseDto.class))).thenReturn(dto);

        mockMvc.perform(post("/api/v1/expenses")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<ExpenseDto> captor = org.mockito.ArgumentCaptor.forClass(ExpenseDto.class);
        verify(expenseService).createExpense(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
    }

    //---------------------------------------------------------
    // updateExpense
    //---------------------------------------------------------

    @Test
    void shouldUpdateExpenseSuccessfully() throws Exception {
        ExpenseDto dto = validDto();
        when(expenseService.updateExpense(any(ExpenseDto.class))).thenReturn(dto);

        mockMvc.perform(put("/api/v1/expenses")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("ext-1"));
    }

    @Test
    void shouldReturnBadRequest_whenExpenseNotFoundOnUpdate() throws Exception {
        when(expenseService.updateExpense(any(ExpenseDto.class)))
                .thenThrow(new IllegalArgumentException("Expense not found"));

        mockMvc.perform(put("/api/v1/expenses")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isBadRequest());
    }

    //---------------------------------------------------------
    // getExpense
    //---------------------------------------------------------

    @Test
    void shouldReturnExpense_whenFound() throws Exception {
        when(expenseService.getExpense("user-1", "ext-1")).thenReturn(validDto());

        mockMvc.perform(get("/api/v1/expenses/ext-1")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("ext-1"));
    }

    @Test
    void shouldReturnBadRequest_whenExpenseNotFound() throws Exception {
        when(expenseService.getExpense("user-1", "missing"))
                .thenThrow(new IllegalArgumentException("Expense not found"));

        mockMvc.perform(get("/api/v1/expenses/missing")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isBadRequest());
    }

    //---------------------------------------------------------
    // deleteExpense
    //---------------------------------------------------------

    @Test
    void shouldDeleteExpense_andReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/expenses/ext-1")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isNoContent());

        verify(expenseService).deleteExpenseByExternalId("user-1", "ext-1");
    }

    //---------------------------------------------------------
    // getSummary
    //---------------------------------------------------------

    @Test
    void shouldReturnSummary() throws Exception {
        Summary summary = Summary.builder()
                .income(BigDecimal.valueOf(1000))
                .expenditure(BigDecimal.valueOf(400))
                .balance(BigDecimal.valueOf(600))
                .totalTransactions(5L)
                .incomeTransactions(2L)
                .expenditureTransactions(3L)
                .build();
        when(expenseService.getSummary("user-1")).thenReturn(summary);

        mockMvc.perform(get("/api/v1/expenses/summary")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(600));
    }

    //---------------------------------------------------------
    // analytics
    //---------------------------------------------------------

    @Test
    void shouldReturnCategoryAnalytics() throws Exception {
        CategoryAnalyticsResponse response =
                new CategoryAnalyticsResponse(Map.of(CategoryType.FOOD, BigDecimal.valueOf(500)));
        when(expenseService.getCategoryAnalytics("user-1")).thenReturn(response);

        mockMvc.perform(get("/api/v1/expenses/analytics/category")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analytics.FOOD").value(500));
    }

    @Test
    void shouldReturnMonthlyAnalytics() throws Exception {
        when(expenseService.getMonthlyAnalytics("user-1"))
                .thenReturn(List.of(new MonthlyAnalyticsDto(2026, 6, BigDecimal.valueOf(750))));

        mockMvc.perform(get("/api/v1/expenses/analytics/monthly")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].month").value(6));
    }

    //---------------------------------------------------------
    // budgets
    //---------------------------------------------------------

    @Test
    void shouldCreateBudget() throws Exception {
        BudgetDto dto = BudgetDto.builder()
                .category(CategoryType.FOOD).amount(BigDecimal.valueOf(1000))
                .month(6).year(2026).build();
        when(budgetService.createBudget("user-1", dto)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/expenses/budgets")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("FOOD"));
    }

    @Test
    void shouldReturnBudgetStatus() throws Exception {
        BudgetStatusDto status = BudgetStatusDto.builder()
                .category(CategoryType.FOOD)
                .budget(BigDecimal.valueOf(1000))
                .spent(BigDecimal.valueOf(200))
                .remaining(BigDecimal.valueOf(800))
                .percentageUsed(20.0)
                .build();
        when(budgetService.getBudgetStatus("user-1", 6, 2026)).thenReturn(List.of(status));

        mockMvc.perform(get("/api/v1/expenses/budgets/status")
                        .header("X-User-Id", "user-1")
                        .param("month", "6")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("FOOD"));
    }

    //---------------------------------------------------------
    // search / listing
    //---------------------------------------------------------

    @Test
    void shouldSearchExpenses_withFilters() throws Exception {
        when(expenseService.searchExpenses(
                eq("user-1"), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(validDto()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/expenses")
                        .header("X-User-Id", "user-1")
                        .param("category", "FOOD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].externalId").value("ext-1"));
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}