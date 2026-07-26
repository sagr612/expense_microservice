package com.greatest.expenseService.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.expenseService.dto.ExpenseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * Test endpoints to exercise the retry + DLT pipeline.
 *
 * Use KafkaTemplate<String, ExpenseDto> (not String) so the message goes
 * through the same serialization path your consumer expects.
 *
 * Three scenarios:
 *  POST /test/publish/success      → consumer processes cleanly, no retry
 *  POST /test/publish/bad-payload  → broken JSON → DeserializationException
 *                                    → goes straight to DLT (not retried)
 *  POST /test/publish/fail         → valid DTO, but ExpenseService throws →
 *                                    retried 3× then sent to DLT
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/test")
public class KafkaTestController {

    // Must match the KafkaTemplate bean in KafkaProducerConfig
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper mapper;

    /**
     * Happy path — sends a well-formed ExpenseDto.
     * Consumer should process it on the first attempt.
     */
    @PostMapping("/publish/success")
    public String publishSuccess(@RequestBody ExpenseDto dto) {
        kafkaTemplate.send("expense.created", dto.getExternalId(), dto);
        return "sent: " + dto.getExternalId();
    }

    /**
     * Triggers DeserializationException → record goes straight to DLT,
     * no retries (it's in the not-retryable list).
     *
     * Useful to verify DLT headers and ExpenseDltConsumer logging.
     */
    @PostMapping("/publish/bad-payload")
    public String publishBadPayload() {
        // Sends a raw string that cannot be deserialized into ExpenseDto
        kafkaTemplate.send("expense.created", "not-valid-json-{{{{");
        return "sent garbage payload";
    }

    /**
     * Sends a DTO with a special marker that makes ExpenseService throw.
     * Add a check in ExpenseService.createExpense():
     *
     *   if ("FAIL_ME".equals(dto.getExternalId())) {
     *       throw new RuntimeException("Simulated processing failure");
     *   }
     *
     * Watch the logs for 3 retry attempts then a DLT publish.
     */
    @PostMapping("/publish/fail")
    public String publishWillFail() {
        ExpenseDto dto = new ExpenseDto();
        dto.setExternalId("FAIL_ME");
        kafkaTemplate.send("expense.created", dto.getExternalId(), dto);
        return "sent FAIL_ME — watch retry logs then DLT";
    }
}