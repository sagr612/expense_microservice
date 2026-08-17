package com.greatest.expenseService.consumer;

import com.greatest.expenseService.dto.ExpenseDto;
import com.greatest.expenseService.services.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseConsumer {
    private final ExpenseService expenseService;

    @KafkaListener(topics = "expense.create.request", groupId = "expenseService-group")
    public void listen(ExpenseDto e) {
         log.info("Received expense event {}", e.getExternalId());

        expenseService.createExpense(e);
    }

}
