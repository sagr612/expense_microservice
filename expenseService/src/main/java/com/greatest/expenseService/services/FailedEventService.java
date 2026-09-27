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
    public void saveRaw(String rawPayload, String topic, int partition, long offset, String errorMessage, String stackTrace) {
        FailedEvents failedEvents = new FailedEvents();
        failedEvents.setTopic(topic);
        failedEvents.setPartition(partition);
        failedEvents.setOffset(offset);
        failedEvents.setEventType("EXPENSE_CREATED");
        failedEvents.setPayload(rawPayload != null ? rawPayload : "null");

        if (errorMessage != null && errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 1000);
        }
        failedEvents.setErrorMessage(errorMessage);
        failedEvents.setStackTrace(stackTrace);
        failedEvents.setRetryCount(0);
        failedEvents.setStatus(FailedEventStatus.FAILED);
        failedEvents.setCreatedAt(LocalDateTime.now());
        failedEvents.setUpdatedAt(LocalDateTime.now());
        repository.save(failedEvents);
    }

}