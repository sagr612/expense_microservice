package com.greatest.expenseService.services;

import com.greatest.expenseService.repository.BudgetRepository;
import com.greatest.expenseService.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCleanupServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private BudgetRepository budgetRepository;

    @InjectMocks
    private UserCleanupService userCleanupService;

    @Test
    void deleteUserData_deletesExpensesAndBudgets() {
        userCleanupService.deleteUserData("user-1");

        verify(expenseRepository).deleteByUserId("user-1");
        verify(budgetRepository).deleteByUserId("user-1");
    }

    @Test
    void deleteUserData_deletesExpensesBeforeBudgets() {
        userCleanupService.deleteUserData("user-1");

        var inOrder = inOrder(expenseRepository, budgetRepository);
        inOrder.verify(expenseRepository).deleteByUserId("user-1");
        inOrder.verify(budgetRepository).deleteByUserId("user-1");
    }

    @Test
    void deleteUserData_propagatesException_whenExpenseDeletionFails() {
        doThrow(new RuntimeException("DB error")).when(expenseRepository).deleteByUserId("user-1");

        assertThrows(RuntimeException.class, () -> userCleanupService.deleteUserData("user-1"));

        verify(budgetRepository, never()).deleteByUserId(any());
    }
}