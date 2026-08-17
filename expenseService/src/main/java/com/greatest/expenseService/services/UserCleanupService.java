package com.greatest.expenseService.services;

import com.greatest.expenseService.repository.BudgetRepository;
import com.greatest.expenseService.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCleanupService {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;

    @Transactional
    public void deleteUserData(String userId) {
        log.info("Deleting data for user {}", userId);
        expenseRepository.deleteByUserId(userId);
        budgetRepository.deleteByUserId(userId);
        log.info("Successfully deleted all data for user {}", userId);
    }
}