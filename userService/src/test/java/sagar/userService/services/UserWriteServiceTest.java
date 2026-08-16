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
import sagar.userService.exceptions.DuplicateResourceException;
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

    private ObjectMapper objectMapper = new ObjectMapper();

    private UserWriteService userWriteService;

    @BeforeEach
    void setUp() {
        // constructed manually (not @InjectMocks) so we control the real ObjectMapper.
        // Constructor order must match field declaration order in UserWriteService
        // (Lombok's @RequiredArgsConstructor generates params in declaration order):
        // userRepository, userDomainService, userDeletedProducer, outboxRepository, objectMapper
        userWriteService = new UserWriteService(userRepository, userDomainService, outboxRepository, objectMapper);

    }
    @Test
    void createUserInternal_throwsDuplicate_whenUserIdAlreadyExists() {
        UserInfoDto dto = UserInfoDto.builder().userId("user-1").email("a@b.com").build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(new UserInfo()));

        assertThatThrownBy(() -> userWriteService.createUserInternal(dto))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("user-1");

        verify(userDomainService, never()).createNewUser(any());
    }

    @Test
    void createUserInternal_savesNewUser_whenUserIdIsFree() {
        UserInfoDto dto = UserInfoDto.builder().userId("user-1").email("a@b.com").build();
        UserInfo created = UserInfo.builder().userId("user-1").email("a@b.com").build();

        when(userRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(userDomainService.createNewUser(dto)).thenReturn(created);
        when(userRepository.save(created)).thenReturn(created);

        UserInfoDto result = userWriteService.createUserInternal(dto);

        assertThat(result.getUserId()).isEqualTo("user-1");
    }

    @Test
    void updateUserInternal_throwsIllegalArgument_whenUserNotFound() {
        UserInfoDto dto = UserInfoDto.builder().userId("user-1").email("a@b.com").build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userWriteService.updateUserInternal("user-1", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user-1");
    }

    @Test
    void updateUserInternal_overridesBodyUserId_withPathVariable() {
        // Security-critical: path variable wins over body value to prevent target-swap attacks.
        UserInfoDto dto = UserInfoDto.builder().userId("attacker-target").email("a@b.com")
                .phoneNumber(9999999999L).firstName("A").lastName("B").build();
        UserInfo existing = UserInfo.builder().userId("user-1").build();
        UserInfo updated = UserInfo.builder().userId("user-1").email("a@b.com").build();

        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));
        when(userDomainService.updateExistingUser(existing, dto)).thenReturn(updated);
        when(userRepository.save(updated)).thenReturn(updated);

        userWriteService.updateUserInternal("user-1", dto);

        // dto.userId should have been silently forced to path var before validation
        assertThat(dto.getUserId()).isEqualTo("user-1");
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
