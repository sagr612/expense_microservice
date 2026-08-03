package com.greatest.expenseService.consumer;

import com.greatest.expenseService.dto.ExpenseDto;
import com.greatest.expenseService.services.ExpenseService;
import com.greatest.expenseService.services.FailedEventService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseDltConsumerTest {

    @Mock
    private ExpenseService expenseService;

    @Mock
    private FailedEventService failedEventService;

    @InjectMocks
    private ExpenseDltConsumer expenseDltConsumer;

    private ExpenseDto expenseDto;

    @BeforeEach
    void setUp() {
        expenseDto = new ExpenseDto();
        expenseDto.setExternalId("ext-123");
        expenseDto.setUserId("user-1");
    }

    @Test
    void shouldSaveFailedEvent() {

        ConsumerRecord<String, ExpenseDto> record = new ConsumerRecord<>("expense.create.request-dlt", 0, 15L, "key", expenseDto);

        String originalTopic = "expense.create.request";
        int partition = 0;
        long offset = 15L;
        String exceptionMessage = "Budget exceeded";
        String stackTrace = "stack trace";

        expenseDltConsumer.handleDlt(
                record,
                originalTopic,
                partition,
                offset,
                exceptionMessage,
                stackTrace
        );

        verify(failedEventService).save(
                expenseDto,
                originalTopic,
                partition,
                offset,
                exceptionMessage,
                stackTrace
        );

        verifyNoInteractions(expenseService);
    }

    @Test
    void shouldSaveFailedEventWhenPayloadIsNull() {

        ConsumerRecord<String, ExpenseDto> record =
                new ConsumerRecord<>("expense.create.request-dlt", 0, 20L, "key", null);

        expenseDltConsumer.handleDlt(
                record,
                "expense.create.request",
                0,
                20L,
                "Deserialization failed",
                "stacktrace"
        );

        verify(failedEventService).save(
                null,
                "expense.create.request",
                0,
                20L,
                "Deserialization failed",
                "stacktrace"
        );

        verifyNoInteractions(expenseService);
    }
}