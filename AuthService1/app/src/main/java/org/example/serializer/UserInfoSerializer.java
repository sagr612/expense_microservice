package org.example.serializer;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.example.eventProducer.UserInfoEvent;
import org.example.model.UserInfoDto;

import java.nio.charset.StandardCharsets;
import java.util.Map;


@Slf4j
public class UserInfoSerializer implements Serializer<UserInfoEvent> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }

    @Override
    public byte[] serialize(String topic, UserInfoEvent data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("Failed to serialize UserInfoEvent for topic {}: {}", topic, ex.getMessage(), ex);
            throw new SerializationException("Error serializing UserInfoEvent", ex);
        }

    }

    @Override
    public void close() {
    }
}
