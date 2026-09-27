package sagar.userService.producer;

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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class UserDeletedProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private OutboxClaimService outboxClaimService;

    @InjectMocks
    private UserDeletedProducer producer;



    private OutboxEvent pending(String id) {
        return OutboxEvent.builder()
                .id(id)
                .aggregateId("user-1")
                .eventType("USER_DELETED")
                .payload("{\"userId\":\"user-1\"}")
                .status(OutboxStatus.PENDING)
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
