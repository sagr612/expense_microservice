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
                .phoneNumber(9999999999L).email("a@b.com").build();

        when(processedEventRepository.existsById("evt-123")).thenReturn(false);
        when(userService.upsertFromEvent(event)).thenReturn(event);

        authServiceConsumer.listen(event);

        verify(userService).upsertFromEvent(event);
        // must record the marker inside the same transaction as the business op
        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-123");
        assertThat(captor.getValue().getConsumerGroup()).isEqualTo("userinfo-consumer-group");
    }

    @Test
    void listen_skipsAlreadyProcessedEvent() {
        UserInfoDto event = UserInfoDto.builder()
                .eventId("evt-123").userId("user-1").build();

        when(processedEventRepository.existsById("evt-123")).thenReturn(true);

        authServiceConsumer.listen(event);

        // this is the whole point of idempotency: never re-run the business op
        verify(userService, never()).upsertFromEvent(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void listen_swallowsDuplicateMarkerInsert_whenTwoConsumersRaceOnSameEvent() {
        UserInfoDto event = UserInfoDto.builder()
                .eventId("evt-123").userId("user-1").build();

        when(processedEventRepository.existsById("evt-123")).thenReturn(false);
        when(userService.upsertFromEvent(event)).thenReturn(event);
        when(processedEventRepository.save(any(ProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // must NOT propagate — the business op is idempotent, so a lost race on the
        // marker insert is harmless. The other consumer already won.
        assertThatCode(() -> authServiceConsumer.listen(event))
                .doesNotThrowAnyException();
    }

    @Test
    void listen_fallsBackToAtLeastOnce_whenEventIdMissing() {
        UserInfoDto event = UserInfoDto.builder()
                .eventId(null).userId("user-1").build();

        authServiceConsumer.listen(event);

        // no eventId → skip dedup entirely, process as at-least-once
        verify(userService).upsertFromEvent(event);
        verify(processedEventRepository, never()).existsById(anyString());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void listen_propagatesUnexpectedException_forRetryByDefaultErrorHandler() {
        UserInfoDto event = UserInfoDto.builder()
                .eventId("evt-123").userId("user-1").build();

        when(processedEventRepository.existsById("evt-123")).thenReturn(false);
        when(userService.upsertFromEvent(event))
                .thenThrow(new RuntimeException("downstream DB unavailable"));

        assertThatThrownBy(() -> authServiceConsumer.listen(event))
                .isInstanceOf(RuntimeException.class);
    }
}
