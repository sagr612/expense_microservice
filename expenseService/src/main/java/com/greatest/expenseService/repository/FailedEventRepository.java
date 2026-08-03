package com.greatest.expenseService.repository;

import com.greatest.expenseService.entities.FailedEvents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvents, Long> {
}