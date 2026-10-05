package com.greatest.notification_service.configs;

import com.greatest.notification_service.consumers.UserDeletedConsumer;
import com.greatest.notification_service.dtos.BudgetAlertEvent;
import com.greatest.notification_service.dtos.ExpenseCreatedEvent;
import com.greatest.notification_service.dtos.UserDeletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer
                (kafkaTemplate, (record, ex)
                        -> new TopicPartition(record.topic() + ".DLT", -1)); // -1 to distrubte dlt to automatic partition as dlt partitions can be less than my orignal topic partition

        ExponentialBackOff backOff = new ExponentialBackOff(2_000L, 2.0);
        backOff.setMaxAttempts(2);
        backOff.setMaxInterval(10_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        handler.addNotRetryableExceptions(DeserializationException.class,
                IllegalArgumentException.class,
                MessageConversionException.class);

        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Retry attempt {} for record offset={} partition={}: {}", deliveryAttempt, record.offset(), record.partition(), ex.getMessage()));

        return handler;
    }

    // =========================
    // Budget Alert Consumer
    // =========================

    @Bean
    public ConsumerFactory<String, BudgetAlertEvent> budgetAlertConsumerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        JsonDeserializer<BudgetAlertEvent> deserializer =
                new JsonDeserializer<>(BudgetAlertEvent.class);

        deserializer.addTrustedPackages("com.greatest.notification_service.dtos");
        deserializer.setUseTypeHeaders(false);

        ErrorHandlingDeserializer<BudgetAlertEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(deserializer);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                errorHandlingDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BudgetAlertEvent>
    budgetAlertKafkaListenerContainerFactory(DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, BudgetAlertEvent>
                factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(budgetAlertConsumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }

    // =========================
    // Expense Created Consumer
    // =========================

    @Bean
    public ConsumerFactory<String, ExpenseCreatedEvent>
    expenseCreatedConsumerFactory() {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        JsonDeserializer<ExpenseCreatedEvent> deserializer = new JsonDeserializer<>(ExpenseCreatedEvent.class);

        deserializer.addTrustedPackages("com.greatest.notification_service.dtos");
        deserializer.setUseTypeHeaders(false);

        ErrorHandlingDeserializer<ExpenseCreatedEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(deserializer);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                errorHandlingDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ExpenseCreatedEvent>
    expenseCreatedKafkaListenerContainerFactory(DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, ExpenseCreatedEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(expenseCreatedConsumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }


    @Bean
    public ConsumerFactory<String, UserDeletedEvent>
    userDeletedEventConsumerFactory() {

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        JsonDeserializer<UserDeletedEvent> deserializer = new JsonDeserializer<>(UserDeletedEvent.class);

        deserializer.addTrustedPackages("com.greatest.notification_service.dtos");
        deserializer.setUseTypeHeaders(false);

        ErrorHandlingDeserializer<UserDeletedEvent> errorHandlingDeserializer = new ErrorHandlingDeserializer<>(deserializer);
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                errorHandlingDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserDeletedEvent>
    userDeletedKafkaListenerContainerFactory(DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, UserDeletedEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(userDeletedEventConsumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }
}