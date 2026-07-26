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
import sagar.userService.dtos.UserDeletedEvent;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.repository.OutboxRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDeletedProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private UserDeletedProducer userDeletedProducer;



    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userDeletedProducer, "topic", "user.deleted");
        ReflectionTestUtils.setField(userDeletedProducer, "objectMapper", objectMapper);
    }
    @Test
    void publish_sendsEventObject_toConfiguredTopic() {
        UserDeletedEvent event = UserDeletedEvent.builder().userId("user-1").build();
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(String.class), any(String.class), any())).thenReturn(future);

        userDeletedProducer.publish(event);

        verify(kafkaTemplate).send(any(String.class), eq("user-1"), eq(event));
    }

    @Test
    void publishPendingEvents_marksEventPublished_whenSendSucceeds() throws Exception {
        OutboxEvent pending = OutboxEvent.builder()
                .id("outbox-1")
                .aggregateId("user-1")
                .eventType("USER_DELETED")
                .payload("{\"userId\":\"user-1\"}")
                .published(false)
                .createdAt(Instant.now())
                .build();

        when(outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(pending));

        CompletableFuture<SendResult<String, Object>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(eq("user.deleted"), eq("user-1"), any())).thenReturn(future);

        userDeletedProducer.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> savedCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().isPublished()).isTrue();
    }

    @Test
    void publishPendingEvents_leavesEventUnpublished_whenSendFails() {
        OutboxEvent pending = OutboxEvent.builder()
                .id("outbox-1")
                .aggregateId("user-1")
                .eventType("USER_DELETED")
                .payload("{\"userId\":\"user-1\"}")
                .published(false)
                .createdAt(Instant.now())
                .build();

        when(outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(pending));

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(eq("user.deleted"), eq("user-1"), any())).thenReturn(failedFuture);

        userDeletedProducer.publishPendingEvents();

        // must NOT mark it published, and must NOT throw out of the scheduled method —
        // a single bad send shouldn't kill the poller for the rest of the batch
        verify(outboxRepository, never()).save(any());
    }


    @Test
    void publishPendingEvents_deserializesPayload_beforeSending() throws Exception {
        String serializedPayload = "{\"userId\":\"user-1\"}";
        OutboxEvent pending = OutboxEvent.builder()
                .id("outbox-1")
                .aggregateId("user-1")
                .eventType("USER_DELETED")
                .payload(serializedPayload)
                .published(false)
                .createdAt(Instant.now())
                .build();

        when(outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(pending));

        CompletableFuture<SendResult<String, Object>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(eq("user.deleted"), eq("user-1"), any())).thenReturn(future);

        userDeletedProducer.publishPendingEvents();

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("user.deleted"), eq("user-1"), valueCaptor.capture());

        // no longer a raw String — it's the deserialized object, so JsonSerializer
        // won't double-encode it on the wire
        assertThat(valueCaptor.getValue()).isNotInstanceOf(String.class);
        assertThat(valueCaptor.getValue()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) valueCaptor.getValue()).get("userId")).isEqualTo("user-1");
    }
}
