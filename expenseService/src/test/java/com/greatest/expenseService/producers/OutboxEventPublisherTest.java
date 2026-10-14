package com.greatest.expenseService.producers;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.expenseService.dto.ExpenseCreatedEvent;
import com.greatest.expenseService.entities.OutboxEvent;
import com.greatest.expenseService.entities.OutboxEvent.OutboxStatus;
import com.greatest.expenseService.producer.OutboxEventPublisher;
import com.greatest.expenseService.repository.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private OutboxRepository outboxRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private OutboxEventPublisher outboxEventPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxEventPublisher, "expenseCreatedTopic", "expense.created");
        ReflectionTestUtils.setField(outboxEventPublisher, "budgetAlertTopic", "budget-alerts");
    }

    @Test
    @DisplayName("Should successfully publish EXPENSE_CREATED event and mark as PUBLISHED")
    void processOne_expenseCreated_success() throws Exception {
        ExpenseCreatedEvent payload = ExpenseCreatedEvent.builder()
                .userId("user-1")
                .amount(BigDecimal.valueOf(150))
                .category("FOOD")
                .externalId("ext-1")
                .build();
        OutboxEvent event = OutboxEvent.builder()
                .id("evt-1")
                .aggregateId("user-1")
                .eventType("EXPENSE_CREATED")
                .payload(objectMapper.writeValueAsString(payload))
                .status(OutboxStatus.PROCESSING)
                .build();

        when(kafkaTemplate.send( any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxEventPublisher.processOne(event);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.isPublished()).isTrue();
        assertThat(event.getLockedUntil()).isNull();
        verify(outboxRepository).save(event);
    }

    @Test
    @DisplayName("Should handle Kafka publish failure by scheduling retry with backoff")
    void processOne_kafkaFailure_retryScheduled() {
        OutboxEvent event = OutboxEvent.builder()
                .id("evt-1")
                .aggregateId("user-1")
                .eventType("EXPENSE_CREATED")
                .payload("{\"userId\":\"user-1\"}")
                .status(OutboxStatus.PROCESSING)
                .attemptCount(0)
                .build();

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka failed"));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn((CompletableFuture) failedFuture);
        outboxEventPublisher.processOne(event);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLockedUntil()).isNull();
        assertThat(event.getLastError()).contains("Kafka failed");
        verify(outboxRepository).save(event);
    }
}
