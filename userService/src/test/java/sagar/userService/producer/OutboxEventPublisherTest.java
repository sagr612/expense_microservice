package sagar.userService.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import sagar.userService.dtos.event.UserDeletedEvent;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.entities.OutboxEvent.OutboxStatus;
import sagar.userService.repository.OutboxRepository;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OutboxEventPublisher publisher;

    @BeforeEach
    public void setup() {
        ReflectionTestUtils.setField(publisher, "topic", "user.deleted");
        ReflectionTestUtils.setField(publisher, "objectMapper", objectMapper);
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
    void processOne_marksEventPublished_onSuccess() {
        OutboxEvent event = pending("outbox-1", 0);
        when(kafkaTemplate.send(anyString(), anyString(), any(UserDeletedEvent.class))).thenReturn(CompletableFuture.completedFuture(null));

        publisher.processOne(event);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.isPublished()).isTrue();
        assertThat(event.getLastError()).isNull();
        verify(outboxRepository).save(event);
    }

    @Test
    void processOne_incrementsAttemptAndReschedules_onTransientFailure() {
        OutboxEvent event = pending("outbox-1", 0);
        when(kafkaTemplate.send(anyString(), anyString(), any(UserDeletedEvent.class))).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka unavailable")));

        publisher.processOne(event);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("Kafka unavailable");
        assertThat(event.getNextRetryAt()).isAfter(Instant.now());
        verify(outboxRepository).save(event);
    }

    @Test
    void processOne_transitionsToDead_afterMaxAttempts() {
        OutboxEvent event = pending("outbox-1", 9);
        when(kafkaTemplate.send(anyString(), anyString(), any(UserDeletedEvent.class))).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Permanent Error")));

        publisher.processOne(event);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(event.getAttemptCount()).isEqualTo(10);
        verify(outboxRepository).save(event);

    }

}
