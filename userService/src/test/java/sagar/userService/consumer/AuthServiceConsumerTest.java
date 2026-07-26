package sagar.userService.consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.services.UserService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceConsumerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthServiceConsumer authServiceConsumer;

    @Test
    void listen_processesEvent_whenDataIsValid() {
        UserInfoDto event = UserInfoDto.builder()
                .userId("user-1")
                .firstName("Sagar")
                .lastName("Sharma")
                .phoneNumber(9999999999L)
                .email("sagar@example.com")
                .build();

        when(userService.createOrUpdateUser(event)).thenReturn(event);

        assertThatCode(() -> authServiceConsumer.listen(event)).doesNotThrowAnyException();

        verify(userService).createOrUpdateUser(event);
    }

    @Test
    void listen_propagatesIllegalArgumentException_forDefaultErrorHandlerToRouteToDLT() {
        // Important: with the simplified listener (no manual try/catch), this exception
        // MUST propagate out of listen() so the container's DefaultErrorHandler can see it
        // and route it straight to the DLT (per KafkaErrorHandlingConfig's
        // addNotRetryableExceptions(IllegalArgumentException.class)).
        UserInfoDto invalidEvent = UserInfoDto.builder().userId(null).build();

        when(userService.createOrUpdateUser(invalidEvent))
                .thenThrow(new IllegalArgumentException("User ID cannot be blank"));

        assertThatThrownBy(() -> authServiceConsumer.listen(invalidEvent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listen_propagatesUnexpectedException_forRetryByDefaultErrorHandler() {
        UserInfoDto event = UserInfoDto.builder().userId("user-1").build();

        when(userService.createOrUpdateUser(event))
                .thenThrow(new RuntimeException("downstream DB unavailable"));

        // this class of exception is NOT in addNotRetryableExceptions, so the container's
        // FixedBackOff(1000L, 3) should retry it three times before going to the DLT —
        // that retry behavior itself lives in the container/error-handler config, not here,
        // but this test proves the listener doesn't swallow the exception before it gets there
        assertThatThrownBy(() -> authServiceConsumer.listen(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("downstream DB unavailable");
    }
}
