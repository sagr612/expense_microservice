package com.greatest.expenseService.config;

import com.greatest.expenseService.consumer.ExpenseDeserializer;
import com.greatest.expenseService.dto.ExpenseDto;
import com.greatest.expenseService.dto.UserDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private final KafkaProperties kafkaProperties;
    // ── Consumer factory ──────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, ExpenseDto> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "expenseService-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ExpenseDeserializer.class);
        // Don't commit the offset until the record is processed successfully
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    // ── Error handler: exponential backoff → DLT ──────────────────────────────

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // Publishes to <original-topic>.DLT automatically (e.g. expense.created-dlt)
        // The failed record + exception info is attached as Kafka headers.
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate);

        // 3 attempts total: initial delivery + 2 retries
        // Waits 2s before retry 1, 4s before retry 2 (doubles each time, max 10s gap)
        ExponentialBackOff backOff = new ExponentialBackOff(2_000L, 2.0);
        backOff.setMaxAttempts(2);          // retries (not counting first attempt)
        backOff.setMaxInterval(10_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Don't retry on these — they will never succeed
        handler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                IllegalArgumentException.class
        );

        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Retry attempt {} for record offset={} partition={}: {}",
                        deliveryAttempt, record.offset(), record.partition(), ex.getMessage()));

        return handler;
    }

    // ── Listener container factory ────────────────────────────────────────────

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ExpenseDto>
    kafkaListenerContainerFactory(DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, ExpenseDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        // Manual offset commits so failed records are not auto-committed
        factory.getContainerProperties()
                .setObservationEnabled(true);
        factory.getContainerProperties()
                .setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, UserDeletedEvent>
    userDeletedConsumerFactory() {

        Map<String, Object> props =
                new HashMap<>(
                        kafkaProperties
                                .buildConsumerProperties()
                );
        props.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );
        JsonDeserializer<UserDeletedEvent>
                deserializer =
                new JsonDeserializer<>(
                        UserDeletedEvent.class
                );

        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<
            String,
            UserDeletedEvent>
    userDeletedKafkaListenerContainerFactory(DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<
                String,
                UserDeletedEvent>
                factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(
                userDeletedConsumerFactory()
        );
        factory.getContainerProperties()
                .setObservationEnabled(true);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties()
                .setAckMode(
                        ContainerProperties.AckMode.RECORD
                );
        return factory;
    }
}