package com.greatest.expenseService.consumer;

import com.greatest.expenseService.dto.ExpenseDto;
import com.greatest.expenseService.services.ExpenseService;
import com.greatest.expenseService.services.FailedEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Listens on the Dead Letter Topic created automatically by
 * DeadLetterPublishingRecoverer (topic name = original topic + ".DLT").
 * <p>
 * Options here:
 * 1. Log & alert (current implementation)
 * 2. Attempt a manual fix and call expenseService again
 * 3. Persist to a "failed_events" DB table for ops review
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseDltConsumer {

    private final ExpenseService expenseService;
    private final FailedEventService failedEventService;

    @KafkaListener(
            topics = "expense.create.request-dlt",
            groupId = "expenseService-dlt-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlt(ConsumerRecord<String, ExpenseDto> record,
                          @Header(KafkaHeaders.DLT_ORIGINAL_TOPIC) String originalTopic,
                          @Header(KafkaHeaders.DLT_ORIGINAL_PARTITION) int partition,
                          @Header(KafkaHeaders.DLT_ORIGINAL_OFFSET) long offset,
                          @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) String exceptionMessageString,
                          @Header(KafkaHeaders.DLT_EXCEPTION_STACKTRACE) String exceptionStackTrace
                          ) {
        ExpenseDto failedExpense = record.value();


        log.error("DLT | externalId={} | originalTopic={} | originalOffset={} | reason={}", failedExpense != null ? failedExpense.getExternalId() : "null", originalTopic, offset, exceptionMessageString);


        failedEventService.save(failedExpense, originalTopic,partition, offset,exceptionMessageString,exceptionStackTrace);

    }


}