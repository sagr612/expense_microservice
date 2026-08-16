package sagar.userService.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.entities.OutboxEvent.OutboxStatus;
import sagar.userService.repository.OutboxRepository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDeletedProducerTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private OutboxRepository outboxRepository;

    @InjectMocks
    private UserDeletedProducer producer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "topic", "user.deleted");
        ReflectionTestUtils.setField(producer, "objectMapper", objectMapper);
    }

    private OutboxEvent pending(String id, int attempts) {
        return OutboxEvent.builder()
                .id(id)
                .aggregateId("user-1")
                .eventType("USER_DELETED")
                .payload("{\"userId\":\"user-1\"}")
                .status(OutboxStatus.PENDING)
                .attemptCount(attempts)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void publishPendingEvents_marksEventPublished_onSuccess() {
        OutboxEvent event = pending("outbox-1", 0);
        when(outboxRepository.findTop50ByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any())).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("user.deleted"), eq("user-1"), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        producer.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(captor.getValue().isPublished()).isTrue();
        assertThat(captor.getValue().getLastError()).isNull();
    }

    @Test
    void publishPendingEvents_incrementsAttemptAndReschedules_onTransientFailure() {
        OutboxEvent event = pending("outbox-1", 2);
        when(outboxRepository.findTop50ByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any())).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(eq("user.deleted"), eq("user-1"), any())).thenReturn(failed);

        producer.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAttemptCount()).isEqualTo(3);
        assertThat(saved.getLastError()).contains("broker unavailable");
        // nextRetryAt should be in the future (backoff applied)
        assertThat(saved.getNextRetryAt()).isAfter(Instant.now());
    }

    @Test
    void publishPendingEvents_transitionsToDead_afterMaxAttempts() {
        // MAX_ATTEMPTS is 10 in the producer; the failing attempt at count=9 becomes attempt 10 → DEAD
        OutboxEvent event = pending("outbox-1", 9);
        when(outboxRepository.findTop50ByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any())).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("permanent broker rejection"));
        when(kafkaTemplate.send(eq("user.deleted"), eq("user-1"), any())).thenReturn(failed);

        producer.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(10);
        assertThat(captor.getValue().getLastError()).contains("permanent broker rejection");
    }

    @Test
    void publishPendingEvents_processesEachEventIndependently() {
        // If one event fails, the next one still gets processed.
        OutboxEvent good = pending("outbox-1", 0);
        OutboxEvent bad  = pending("outbox-2", 0);
        when(outboxRepository.findTop50ByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any())).thenReturn(List.of(bad, good));

        CompletableFuture<SendResult<String, Object>> ok =
                CompletableFuture.completedFuture(mock(SendResult.class));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("boom"));

        when(kafkaTemplate.send(eq("user.deleted"), eq("user-1"), any()))
                .thenReturn(failed, ok);  // first call fails, second succeeds

        producer.publishPendingEvents();

        // BOTH events should be saved (bad → retry, good → published)
        verify(outboxRepository, times(2)).save(any());
    }

    @Test
    void publishPendingEvents_doesNothing_whenNoPendingEvents() {
        when(outboxRepository.findTop50ByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any())).thenReturn(List.of());

        producer.publishPendingEvents();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(outboxRepository, never()).save(any());
    }
}
