package com.greatest.expenseService.repository;

import com.greatest.expenseService.dto.CategoryAnalyticsDto;
import com.greatest.expenseService.dto.Summary;
import com.greatest.expenseService.dto.SummaryProjection;
import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.Expense;
import com.greatest.expenseService.entities.ExpenseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, String>, JpaSpecificationExecutor<Expense> {

    Page<Expense> findByUserId(String userId, Pageable pageable);


    Optional<Expense> findByUserIdAndExternalId(String userId, String externalId);




    Long countByUserId(String userId);

    @Query("""
            SELECT new com.greatest.expenseService.dto.CategoryAnalyticsDto(
            e.category,
            SUM(e.amount)
            )
            FROM Expense e
            WHERE e.userId = :userId
            AND e.type = :type
            GROUP BY e.category
            ORDER BY SUM(e.amount) DESC
            """)
    List<CategoryAnalyticsDto> getCategoryAnalytics(
            String userId,
            ExpenseType type
    );


    @Query("""
            SELECT
            EXTRACT(YEAR FROM e.transactionDate),
            EXTRACT(MONTH FROM e.transactionDate),
            SUM(e.amount)
            FROM Expense e
            WHERE e.userId = :userId
            AND e.type = :type
            GROUP BY
            EXTRACT(YEAR FROM e.transactionDate),
            EXTRACT(MONTH FROM e.transactionDate)
            ORDER BY
            EXTRACT(YEAR FROM e.transactionDate),
            EXTRACT(MONTH FROM e.transactionDate)
            """)
    List<Object[]> getMonthlyAnalytics(
            String userId,
            ExpenseType type
    );


    @Query("""
            SELECT new com.greatest.expenseService.dto.SummaryProjection(
                COALESCE(SUM(CASE WHEN e.type = 'INCOME' THEN e.amount ELSE 0 END),0),
                COALESCE(SUM(CASE WHEN e.type = 'EXPENSE' THEN e.amount ELSE 0 END),0),
                COUNT(e),
                SUM(CASE WHEN e.type = 'INCOME' THEN 1 ELSE 0 END),
                SUM(CASE WHEN e.type = 'EXPENSE' THEN 1 ELSE 0 END)
            )
            FROM Expense e
            WHERE e.userId = :userId
            """)
    SummaryProjection getSummaryByUserId(@Param("userId") String userId);


    @Query("""
            SELECT COALESCE(SUM(e.amount),0)
            FROM Expense e
            WHERE e.userId = :userId
            AND e.category = :category
            AND e.type = 'EXPENSE'
            AND EXTRACT(MONTH FROM e.transactionDate) = :month
            AND EXTRACT(YEAR FROM e.transactionDate) = :year
            """)
    BigDecimal getSpentAmount(
            String userId,
            CategoryType category,
            int month,
            int year
    );

    void deleteByUserId(String userId);
}
