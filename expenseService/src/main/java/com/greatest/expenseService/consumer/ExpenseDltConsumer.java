package com.greatest.expenseService.consumer;

import com.greatest.expenseService.dto.ExpenseDto;
import com.greatest.expenseService.services.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Listens on the Dead Letter Topic created automatically by
 * DeadLetterPublishingRecoverer (topic name = original topic + ".DLT").
 *
 * Options here:
 *  1. Log & alert (current implementation)
 *  2. Attempt a manual fix and call expenseService again
 *  3. Persist to a "failed_events" DB table for ops review
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseDltConsumer {

    private final ExpenseService expenseService;

    @KafkaListener(
            topics = "expense.created-dlt",
            groupId = "expenseService-dlt-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlt(ConsumerRecord<String, ExpenseDto> record) {
        ExpenseDto failedExpense = record.value();

        // Spring Kafka attaches failure metadata as headers
        String originalTopic  = headerAsString(record, "kafka_dlt-original-topic");
        String originalOffset = headerAsLong(record,   "kafka_dlt-original-offset");
        String exceptionMsg   = headerAsString(record, "kafka_dlt-exception-message");

        log.error(
                "DLT | externalId={} | originalTopic={} | originalOffset={} | reason={}",
                failedExpense != null ? failedExpense.getExternalId() : "null",
                originalTopic, originalOffset, exceptionMsg
        );

        // ── Option A: just alert ─────────────────────────────────────────────
        // Send to Slack / PagerDuty / persist to DB here

        // ── Option B: attempt recovery ───────────────────────────────────────
        // Uncomment if you want to auto-retry with corrected logic:
        // if (failedExpense != null) {
        //     expenseService.createExpense(failedExpense);
        // }
    }

    // ── Header helpers ────────────────────────────────────────────────────────

    private String headerAsString(ConsumerRecord<?, ?> record, String key) {
        Header h = record.headers().lastHeader(key);
        return h != null ? new String(h.value(), StandardCharsets.UTF_8) : "unknown";
    }

    private String headerAsLong(ConsumerRecord<?, ?> record, String key) {
        Header h = record.headers().lastHeader(key);
        if (h == null) return "unknown";
        try {
            return String.valueOf(ByteBuffer.wrap(h.value()).getLong());
        } catch (Exception e) {
            return new String(h.value(), StandardCharsets.UTF_8);
        }
    }
}