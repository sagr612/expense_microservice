package com.greatest.notification_service.configs;

import com.greatest.notification_service.consumers.UserDeletedConsumer;
import com.greatest.notification_service.dtos.BudgetAlertEvent;
import com.greatest.notification_service.dtos.ExpenseCreatedEvent;
import com.greatest.notification_service.dtos.UserDeletedEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class  KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    // =========================
    // Budget Alert Consumer
    // =========================

    @Bean
    public ConsumerFactory<String, BudgetAlertEvent> budgetAlertConsumerFactory() {

        Map<String, Object> props =
                new HashMap<>(kafkaProperties.buildConsumerProperties());

        JsonDeserializer<BudgetAlertEvent> deserializer =
                new JsonDeserializer<>(BudgetAlertEvent.class);

        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BudgetAlertEvent>
    budgetAlertKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, BudgetAlertEvent>
                factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(
                budgetAlertConsumerFactory()
        );
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }

    // =========================
    // Expense Created Consumer
    // =========================

    @Bean
    public ConsumerFactory<String, ExpenseCreatedEvent>
    expenseCreatedConsumerFactory() {

        Map<String, Object> props =
                new HashMap<>(kafkaProperties.buildConsumerProperties());

        JsonDeserializer<ExpenseCreatedEvent> deserializer =
                new JsonDeserializer<>(ExpenseCreatedEvent.class);

        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ExpenseCreatedEvent>
    expenseCreatedKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, ExpenseCreatedEvent>
                factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(
                expenseCreatedConsumerFactory()
        );
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }


    @Bean
    public ConsumerFactory<String, UserDeletedEvent>
    userDeletedEventConsumerFactory() {

        Map<String, Object> props =
                new HashMap<>(kafkaProperties.buildConsumerProperties());

        JsonDeserializer<UserDeletedEvent> deserializer =
                new JsonDeserializer<>(UserDeletedEvent.class);

        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserDeletedEvent>
    userDeletedKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, UserDeletedEvent>
                factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(
                userDeletedEventConsumerFactory()
        );
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }
}