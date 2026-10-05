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


@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseDltConsumer {

    private final ExpenseService expenseService;
    private final FailedEventService failedEventService;

    @KafkaListener(
            topics = {
                    "${spring.kafka.topic-expense-create-request.name:expense.create.request}.DLT",
                    "${spring.kafka.topic-user-deleted.name:user.deleted}.DLT"
            },
            groupId = "expenseService-dlt-group",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void handleDlt(ConsumerRecord<String, String> record,
                          @Header(KafkaHeaders.DLT_ORIGINAL_TOPIC) String originalTopic,
                          @Header(KafkaHeaders.DLT_ORIGINAL_PARTITION) int partition,
                          @Header(KafkaHeaders.DLT_ORIGINAL_OFFSET) long offset,
                          @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) String exceptionMessageString,
                          @Header(KafkaHeaders.DLT_EXCEPTION_STACKTRACE) String exceptionStackTrace
    ) {
        String rawPayload = record.value();

        log.error("DLT | originalTopic={} | originalOffset={} | reason={}", originalTopic, offset, exceptionMessageString);

        failedEventService.saveRaw(rawPayload, originalTopic, partition, offset, exceptionMessageString, exceptionStackTrace);

    }


}