package com.greatest.expenseService.producer;

import com.greatest.expenseService.dto.BudgetAlertEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BudgetAlertProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topic-budget-alert.name}")
    private String topic;

    public void publish(BudgetAlertEvent event) {

        kafkaTemplate.send(
                topic,
                event.getUserId(),
                event
        );
    }
}