package org.example.eventProducer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserInfoProducerTest {

    @Mock
    private KafkaTemplate<String, UserInfoEvent> kafkaTemplate;

    @InjectMocks
    private UserInfoProducer producer;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(producer, "topicJsonName", "user.created");
    }

    @Test
    void shouldSendUserEventToKafka() {

        UserInfoEvent event = UserInfoEvent.builder()
                .userId("user-1")
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .phoneNumber(9999999999L)
                .build();

        producer.sendEventToKafka(event);

        ArgumentCaptor<Message<UserInfoEvent>> captor = ArgumentCaptor.forClass(Message.class);

        verify(kafkaTemplate, times(1)).send(captor.capture());

        Message<UserInfoEvent> message = captor.getValue();

        assertEquals(event, message.getPayload());

        assertEquals("user.created", message.getHeaders().get(KafkaHeaders.TOPIC));
    }

    @Test
    void shouldSendExactlyOneMessage() {

        UserInfoEvent event = UserInfoEvent.builder()
                .userId("1")
                .build();

        producer.sendEventToKafka(event);

        verify(kafkaTemplate, times(1))
                .send(any(Message.class));
        verifyNoMoreInteractions(kafkaTemplate);
    }
}