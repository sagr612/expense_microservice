package com.greatest.expenseService.consumer;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.greatest.expenseService.dto.ExpenseDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.kafka.support.serializer.DeserializationException;

@Slf4j
public class ExpenseDeserializer implements Deserializer<ExpenseDto> {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public ExpenseDto deserialize(String s, byte[] data) {
        if (data == null) {
            log.warn("Null data received for deserialization on topic: {}", s);
            return null;
        }
        try {
            log.info(
                    "Received payload: {}",
                    new String(data)
            );
            ExpenseDto expense = MAPPER.readValue(data, ExpenseDto.class);
            log.debug("Successfully deserialized ExpenseDto from topic: {}", s);
            return expense;
        } catch (Exception ex) {
            throw new DeserializationException("Failed to deserialize", data, false, ex);
//            throw new RuntimeException("Deserialization failed for topic: " + s, ex);
        }
    }

    @Override
    public void close() {
        // No resources to close
    }
}
