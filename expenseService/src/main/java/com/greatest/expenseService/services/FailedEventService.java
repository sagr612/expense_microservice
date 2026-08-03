package com.greatest.expenseService.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.expenseService.dto.ExpenseDto;
import com.greatest.expenseService.entities.FailedEventStatus;
import com.greatest.expenseService.entities.FailedEvents;
import com.greatest.expenseService.repository.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class FailedEventService {

    private final FailedEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(ExpenseDto expense, String topic, int partition, long offset, String errorMessage, String stackTrace) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(expense);
            log.info("saving expense to failed event payload {}", payload);
        } catch (JsonProcessingException e) {
            log.error("Unable to serialize failed event", e);
            payload = "Unable to serialize payload";
        }
        FailedEvents failedEvent = new FailedEvents();
        failedEvent.setTopic(topic);

        failedEvent.setPartition(partition);

        failedEvent.setOffset(offset);

        failedEvent.setEventType("EXPENSE_CREATED");

        failedEvent.setPayload(payload);

        failedEvent.setErrorMessage(errorMessage);

        failedEvent.setStackTrace(stackTrace);

        failedEvent.setRetryCount(0);

        failedEvent.setStatus(FailedEventStatus.FAILED);

        failedEvent.setCreatedAt(LocalDateTime.now());

        failedEvent.setUpdatedAt(LocalDateTime.now());
        repository.save(failedEvent);
    }

}