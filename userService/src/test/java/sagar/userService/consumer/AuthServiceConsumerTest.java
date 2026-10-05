package sagar.userService.consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.entities.ProcessedEvent;
import sagar.userService.repository.ProcessedEventRepository;
import sagar.userService.services.UserService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ExtendWith(MockitoExtension.class)
class AuthServiceConsumerTest {

    @Mock private UserService userService;
    @Mock private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private AuthServiceConsumer authServiceConsumer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authServiceConsumer, "consumerGroup", "userinfo-consumer-group");
    }

    @Test
    void listen_processesNewEvent_andRecordsIdempotencyMarker() {
        UserInfoDto event = UserInfoDto.builder()
                .eventId("evt-123")
                .userId("user-1").firstName("A").lastName("B")
                .phoneNumber("9999999999").email("a@b.com").build();

        when(processedEventRepository.insertIfAbsent(eq("evt-123"), eq("userinfo-consumer-group"), any(Instant.class)))
                .thenReturn(1);
        when(userService.upsertFromEvent(event)).thenReturn(event);

        authServiceConsumer.listen(event);

        verify(userService).upsertFromEvent(event);
        verify(processedEventRepository).insertIfAbsent(eq("evt-123"), eq("userinfo-consumer-group"), any(Instant.class));
    }

    @Test
    void listen_skipsAlreadyProcessedEvent() {
        UserInfoDto event = UserInfoDto.builder()
                .eventId("evt-123").userId("user-1").build();

        when(processedEventRepository.insertIfAbsent(eq("evt-123"), eq("userinfo-consumer-group"), any(Instant.class)))
                .thenReturn(0);

        authServiceConsumer.listen(event);


        verify(userService, never()).upsertFromEvent(any());
    }



    @Test
    void listen_fallsBackToAtLeastOnce_whenEventIdMissing() {
        UserInfoDto event = UserInfoDto.builder()
                .eventId(null).userId("user-1").build();

        authServiceConsumer.listen(event);

        // no eventId → skip dedup entirely, process as at-least-once
        verify(userService).upsertFromEvent(event);
        verifyNoInteractions(processedEventRepository);
    }

    @Test
    void listen_propagatesUnexpectedException_forRetryByDefaultErrorHandler() {
        UserInfoDto event = UserInfoDto.builder()
                .eventId("evt-123").userId("user-1").build();

        when(processedEventRepository.insertIfAbsent(eq("evt-123"), eq("userinfo-consumer-group"), any(Instant.class)))
                .thenReturn(1);

        when(userService.upsertFromEvent(event))
                .thenThrow(new RuntimeException("downstream DB unavailable"));

        assertThatThrownBy(() -> authServiceConsumer.listen(event))
                .isInstanceOf(RuntimeException.class);
    }
}
