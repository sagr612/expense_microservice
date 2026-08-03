package com.greatest.expenseService.dto;

import com.greatest.expenseService.entities.CategoryType;
import com.greatest.expenseService.entities.Expense;
import com.greatest.expenseService.entities.ExpenseType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class ExpenseSpecification {

    public static Specification<Expense> hasUserId(String userId) {
        return (root, query, cb) ->
                cb.equal(root.get("userId"), userId);
    }

    public static Specification<Expense> hasCategory(CategoryType category) {
        return (root, query, cb) ->
                category == null
                        ? null
                        : cb.equal(root.get("category"), category);
    }

    public static Specification<Expense> hasType(ExpenseType type) {
        return (root, query, cb) ->
                type == null
                        ? null
                        : cb.equal(root.get("type"), type);
    }

    public static Specification<Expense> minAmount(BigDecimal amount) {
        return (root, query, cb) ->
                amount == null
                        ? null
                        : cb.greaterThanOrEqualTo(root.get("amount"), amount);
    }

    public static Specification<Expense> maxAmount(BigDecimal amount) {
        return (root, query, cb) ->
                amount == null
                        ? null
                        : cb.lessThanOrEqualTo(root.get("amount"), amount);
    }

    public static Specification<Expense> fromDate(LocalDate date) {
        return (root, query, cb) ->
                date == null
                        ? null
                        : cb.greaterThanOrEqualTo(root.get("transactionDate"), date);
    }

    public static Specification<Expense> toDate(LocalDate date) {
        return (root, query, cb) ->
                date == null
                        ? null
                        : cb.lessThanOrEqualTo(root.get("transactionDate"), date);
    }
}