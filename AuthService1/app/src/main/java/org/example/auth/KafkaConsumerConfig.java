package org.example.auth;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.model.UserDeletedEvent;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;

    @Bean
    public ConsumerFactory<String, UserDeletedEvent>
    userDeletedConsumerFactory() {

        Map<String, Object> props =
                new HashMap<>(
                        kafkaProperties.buildConsumerProperties()
                );

        props.put(
                org.apache.kafka.clients.consumer.ConsumerConfig
                        .ENABLE_AUTO_COMMIT_CONFIG,
                false
        );

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
    public ConcurrentKafkaListenerContainerFactory<
            String,
            UserDeletedEvent>
    userDeletedKafkaListenerContainerFactory(
    ) {

        ConcurrentKafkaListenerContainerFactory<
                String,
                UserDeletedEvent>
                factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(
                userDeletedConsumerFactory()
        );



        factory.getContainerProperties()
                .setAckMode(
                        ContainerProperties.AckMode.RECORD
                );

        factory.getContainerProperties()
                .setObservationEnabled(true);

        return factory;
    }
}