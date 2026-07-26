package sagar.userService.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.entities.UserInfo;
import sagar.userService.producer.UserDeletedProducer;
import sagar.userService.repository.OutboxRepository;
import sagar.userService.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserWriteServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserDomainService userDomainService;
    @Mock
    private OutboxRepository outboxRepository;

    // still present as an unused field on UserWriteService (dead code flagged during review) —
    // mocked here purely so the constructor call compiles against the real class as-is
    @Mock
    private UserDeletedProducer userDeletedProducer;

    private ObjectMapper objectMapper = new ObjectMapper();

    private UserWriteService userWriteService;

    @BeforeEach
    void setUp() {
        // constructed manually (not @InjectMocks) so we control the real ObjectMapper.
        // Constructor order must match field declaration order in UserWriteService
        // (Lombok's @RequiredArgsConstructor generates params in declaration order):
        // userRepository, userDomainService, userDeletedProducer, outboxRepository, objectMapper
        userWriteService = new UserWriteService(
                userRepository, userDomainService, outboxRepository, objectMapper);
    }

    @Test
    void createOrUpdateUserInternal_updatesExistingUser_whenUserAlreadyExists() {
        UserInfoDto dto = UserInfoDto.builder().userId("user-1").email("a@b.com").build();
        UserInfo existing = UserInfo.builder().userId("user-1").build();
        UserInfo updated = UserInfo.builder().userId("user-1").email("a@b.com").build();

        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));
        when(userDomainService.updateExistingUser(existing, dto)).thenReturn(updated);
        when(userRepository.save(updated)).thenReturn(updated);

        UserInfoDto result = userWriteService.createOrUpdateUserInternal(dto);

        assertThat(result.getUserId()).isEqualTo("user-1");
        verify(userDomainService).validateUserDto(dto);
        verify(userDomainService).updateExistingUser(existing, dto);
        verify(userDomainService, never()).createNewUser(any());
    }

    @Test
    void createOrUpdateUserInternal_createsNewUser_whenUserDoesNotExist() {
        UserInfoDto dto = UserInfoDto.builder().userId("user-2").email("c@d.com").build();
        UserInfo created = UserInfo.builder().userId("user-2").email("c@d.com").build();

        when(userRepository.findByUserId("user-2")).thenReturn(Optional.empty());
        when(userDomainService.createNewUser(dto)).thenReturn(created);
        when(userRepository.save(created)).thenReturn(created);

        UserInfoDto result = userWriteService.createOrUpdateUserInternal(dto);

        assertThat(result.getUserId()).isEqualTo("user-2");
        verify(userDomainService).createNewUser(dto);
        verify(userDomainService, never()).updateExistingUser(any(), any());
    }

    @Test
    void createOrUpdateUserInternal_propagatesValidationFailure_withoutTouchingRepository() {
        UserInfoDto invalidDto = UserInfoDto.builder().userId(null).build();

        doThrow(new IllegalArgumentException("User ID cannot be blank"))
                .when(userDomainService).validateUserDto(invalidDto);

        assertThatThrownBy(() -> userWriteService.createOrUpdateUserInternal(invalidDto))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void deleteUserInternal_deletesUserAndWritesOutboxEvent() {
        UserInfo existing = UserInfo.builder().userId("user-1").build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));

        userWriteService.deleteUserInternal("user-1");

        verify(userRepository).delete(existing);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent savedEvent = captor.getValue();
        assertThat(savedEvent.getAggregateId()).isEqualTo("user-1");
        assertThat(savedEvent.getEventType()).isEqualTo("USER_DELETED");
        assertThat(savedEvent.isPublished()).isFalse();
        assertThat(savedEvent.getPayload()).contains("user-1"); // sanity check on serialized JSON
    }

    @Test
    void deleteUserInternal_throwsAndSkipsOutbox_whenUserNotFound() {
        when(userRepository.findByUserId("missing-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userWriteService.deleteUserInternal("missing-user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing-user");

        verify(userRepository, never()).delete(any());
        verifyNoInteractions(outboxRepository);
    }
}
