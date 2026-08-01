package com.greatest.expenseService.consumer;

import com.greatest.expenseService.dto.ExpenseDto;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.DeserializationException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpenseDeserializerTest {

    private final ExpenseDeserializer deserializer = new ExpenseDeserializer();

    @Test
    void deserialize_returnsNull_whenDataIsNull() {
        assertThat(deserializer.deserialize("expense.created", null)).isNull();
    }

    @Test
    void deserialize_returnsDto_whenJsonIsValid() {
        String json = """
            {
              "userId": "user-1",
              "amount": 100.50,
              "currency": "INR",
              "type": "EXPENSE",
              "category": "FOOD",
              "title": "Lunch"
            }
            """;

        ExpenseDto result = deserializer.deserialize("expense.created", json.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getTitle()).isEqualTo("Lunch");
    }

    @Test
    void deserialize_throwsDeserializationException_onMalformedJson_forDltRouting() {
        byte[] malformed = "{ not valid json".getBytes(StandardCharsets.UTF_8);

        // Must be DeserializationException (not a plain RuntimeException) so
        // Spring Kafka's error handling routes it straight to the DLT
        assertThatThrownBy(() -> deserializer.deserialize("expense.created", malformed))
                .isInstanceOf(DeserializationException.class);
    }

    @Test
    void deserialize_handlesLocalDateFields_viaJavaTimeModule() {
        String json = """
            {
              "userId": "user-1",
              "amount": 50,
              "type": "EXPENSE",
              "category": "FOOD",
              "title": "Coffee",
              "transactionDate": "2026-06-15"
            }
            """;

        ExpenseDto result = deserializer.deserialize("expense.created", json.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getTransactionDate()).isEqualTo(java.time.LocalDate.of(2026, 6, 15));
    }
}