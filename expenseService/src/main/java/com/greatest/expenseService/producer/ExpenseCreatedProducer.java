package com.greatest.expenseService.producer;

import com.greatest.expenseService.dto.ExpenseCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExpenseCreatedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topic-expense-created.name}")
    private String topic;

    public void publish(
            ExpenseCreatedEvent event
    ) {

        kafkaTemplate.send(
                topic,
                event.getUserId(),
                event
        );
    }
}