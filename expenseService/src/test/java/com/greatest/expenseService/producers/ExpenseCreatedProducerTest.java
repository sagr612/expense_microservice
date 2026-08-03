package com.greatest.expenseService.producers;

import com.greatest.expenseService.dto.ExpenseCreatedEvent;
import com.greatest.expenseService.producer.ExpenseCreatedProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpenseCreatedProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ExpenseCreatedProducer producer;

    @Test
    void publish_sendsEvent_toConfiguredTopic_keyedByUserId() {
        ReflectionTestUtils.setField(producer, "topic", "expense.created");

        ExpenseCreatedEvent event = ExpenseCreatedEvent.builder()
                .userId("user-1")
                .amount(BigDecimal.valueOf(100))
                .category("FOOD")
                .externalId("ext-1")
                .build();

        producer.publish(event);

        verify(kafkaTemplate).send("expense.created", "user-1", event);
    }
}