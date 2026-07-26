package com.greatest.expenseService.repository;

import com.greatest.expenseService.entities.Budget;
import com.greatest.expenseService.entities.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository
        extends JpaRepository<Budget, Long> {

    Optional<Budget> findByUserIdAndCategoryAndMonthAndYear(
            String userId,
            CategoryType category,
            Integer month,
            Integer year
    );

    List<Budget> findByUserIdAndMonthAndYear(
            String userId,
            Integer month,
            Integer year
    );
    void deleteByUserId(String userId);
}