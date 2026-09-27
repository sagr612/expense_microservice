package org.example.eventProducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entities.OutboxEvent;
import org.example.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserInfoProducerTest {

    @Mock
    private KafkaTemplate<String, UserInfoEvent> kafkaTemplate;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;


    @Mock
    OutboxClaimService outboxClaimService;

    @Mock
    private ObjectMapper objectMapper;
    @InjectMocks
    private UserInfoProducer producer;


    private OutboxEvent pending(String id) {
        return OutboxEvent.builder()
                .id(id)
                .aggregateId("user-1")
                .eventType("USER_CREATED")
                .payload("{\"userId\":\"user-1\"}")
                .status(OutboxEvent.OutboxStatus.PENDING)
                .attemptCount(0)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .createdAt(Instant.now())
                .build();
    }



    @Test
    void publishPendingEvents_doesNothing_whenNoPendingEvents() {
        when(outboxClaimService.claimEvents(any(), any(), any(Integer.class))).thenReturn(List.of());
        producer.publishPendingEvents();
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void publishPendingEvents_delegatesEachEventToPublisher() {
        OutboxEvent event1 = pending("event-1");
        OutboxEvent event2 = pending("event-2");

        when(outboxClaimService.claimEvents(any(), any(), any(Integer.class))).thenReturn(List.of(event1, event2));

        producer.publishPendingEvents();

        verify(outboxEventPublisher).processOne(event1);
        verify(outboxEventPublisher).processOne(event2);
    }
}
