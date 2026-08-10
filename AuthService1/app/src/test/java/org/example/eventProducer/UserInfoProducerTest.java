package org.example.eventProducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entities.OutboxEvent;
import org.example.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserInfoProducerTest {

    @Mock
    private KafkaTemplate<String, UserInfoEvent> kafkaTemplate;


    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;
    @InjectMocks
    private UserInfoProducer producer;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(producer, "topic", "user.created");
    }

    @Test
    void publishPendingEvents_shouldPublishAndMarkAsPublished() throws Exception {
        // given
        UserInfoEvent payload = UserInfoEvent.builder()
                .userId("u1").firstName("A").lastName("B")
                .email("a@b.com").phoneNumber("9999999999").build();
        String json = new ObjectMapper().writeValueAsString(payload);
        OutboxEvent event = OutboxEvent.builder()
                .id("event-1").aggregateId("u1").eventType("USER_CREATED")
                .payload(json).published(false).build();

        when(outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(objectMapper.readValue(anyString(), eq(UserInfoEvent.class)))
                .thenReturn(payload);

        // Mock the CompletableFuture return so .get() doesn't hang
        CompletableFuture<SendResult<String, UserInfoEvent>> future = CompletableFuture
                .completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // when
        producer.publishPendingEvents();

        // then
        verify(kafkaTemplate).send(anyString(), eq("u1"), eq(payload));
        verify(outboxRepository).save(argThat(OutboxEvent::isPublished));
    }


}