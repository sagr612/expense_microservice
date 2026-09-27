package sagar.userService.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.sql.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.dtos.request.CreateUserRequest;
import sagar.userService.dtos.request.UpdateUserRequest;
import sagar.userService.dtos.response.UserProfileImageResponse;
import sagar.userService.dtos.response.UserResponse;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.entities.UserInfo;
import sagar.userService.exceptions.DuplicateResourceException;
import sagar.userService.repository.OutboxRepository;
import sagar.userService.repository.UserRepository;

import java.util.Map;
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

    @Mock
    private UserWriteService userWriteService;
    @Mock
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        // constructed manually (not @InjectMocks) so we control the real ObjectMapper.
        // Constructor order must match field declaration order in UserWriteService
        // (Lombok's @RequiredArgsConstructor generates params in declaration order):
        // userRepository, userDomainService, userDeletedProducer, outboxRepository, objectMapper
        userWriteService = new UserWriteService(userRepository, userDomainService, outboxRepository, objectMapper, cloudinaryService);

    }
    @Test
    void createUserInternal_throwsDuplicate_whenUserIdAlreadyExists() {
        CreateUserRequest dto = CreateUserRequest.builder().userId("user-1").email("a@b.com").build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(new UserInfo()));

        assertThatThrownBy(() -> userWriteService.createUserInternal(dto))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("user-1");

        verify(userDomainService, never()).createNewUser(dto);
    }

    @Test
    void createUserInternal_savesNewUser_whenUserIdIsFree() {
        CreateUserRequest dto = CreateUserRequest.builder().userId("user-1").email("a@b.com").build();
        UserInfo created = UserInfo.builder().userId("user-1").email("a@b.com").build();

        when(userRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(userDomainService.createNewUser(dto)).thenReturn(created);
        when(userRepository.save(created)).thenReturn(created);

        UserResponse result = userWriteService.createUserInternal(dto);

        assertThat(result.getUserId()).isEqualTo("user-1");
    }

    @Test
    void updateUserInternal_throwsIllegalArgument_whenUserNotFound() {
        UpdateUserRequest dto = UpdateUserRequest.builder().email("a@b.com").build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userWriteService.updateUserInternal("user-1", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user-1");
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
        verify(userDomainService, never()).createNewUser(dto);
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
        verify(userDomainService, never()).updateExistingUser(created, dto);
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


    @Test
    void updateProfileImage_uploadsNewImage_andDeletesOldOne_whenOldImageExists() {

        UserInfo user = UserInfo.builder()
                .userId("user-1")
                .profilePic("old-url")
                .profileImagePublicId("old-public-id")
                .build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("image", "avatar.png", "image/png", "bytes".getBytes());
        Map<String, Object> uploadResult = Map.of(
                "secure_url", "new-url",
                "public_id", "new-public-id");
        when(cloudinaryService.upload(file)).thenReturn(uploadResult);

        UserProfileImageResponse response = userWriteService.updateProfileImageInternal("user-1", file);

        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.profileImageUrl()).isEqualTo("new-url");

        // verify ordering intent: upload+save happened, THEN the old image was deleted
        verify(userRepository).save(user);
        verify(cloudinaryService).delete("old-public-id");
        assertThat(user.getProfilePic()).isEqualTo("new-url");
        assertThat(user.getProfileImagePublicId()).isEqualTo("new-public-id");
    }

    @Test
    void updateProfileImage_skipsDelete_whenUserHadNoPreviousImage() {
        UserInfo user = UserInfo.builder()
                .userId("user-1")
                .profilePic(null)
                .profileImagePublicId(null)
                .build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("image", "avatar.png", "image/png", "bytes".getBytes());
        when(cloudinaryService.upload(file)).thenReturn(
                Map.of("secure_url", "new-url", "public_id", "new-public-id"));

        userWriteService.updateProfileImageInternal("user-1", file);

        verify(cloudinaryService, never()).delete(any());
    }

    @Test
    void updateProfileImage_doesNotSaveOrDelete_whenUploadFails() {
        UserInfo user = UserInfo.builder()
                .userId("user-1")
                .profileImagePublicId("old-public-id")
                .build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("image", "avatar.png", "image/png", "bytes".getBytes());
        when(cloudinaryService.upload(file)).thenThrow(new RuntimeException("Cloudinary upload failed"));

        assertThatThrownBy(() -> userWriteService.updateProfileImageInternal("user-1", file))
                .isInstanceOf(RuntimeException.class);

        // this is the regression test for the upload-before-delete fix — if upload fails,
        // the user must keep their old image: no save, and definitely no delete of the old one
        verify(userRepository, never()).save(any());
        verify(cloudinaryService, never()).delete(any());
    }

    @Test
    void updateProfileImage_swallowsDeleteFailure_andStillReturnsSuccessResponse() {

        UserInfo user = UserInfo.builder()
                .userId("user-1")
                .profileImagePublicId("old-public-id")
                .build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("image", "avatar.png", "image/png", "bytes".getBytes());
        when(cloudinaryService.upload(file)).thenReturn(
                Map.of("secure_url", "new-url", "public_id", "new-public-id"));
        doThrow(new RuntimeException("Cloudinary delete failed"))
                .when(cloudinaryService).delete("old-public-id");

        // must NOT propagate — the new image is already safely saved, so a cleanup
        // failure on the OLD image should not fail the whole request1
        UserProfileImageResponse response = userWriteService.updateProfileImageInternal("user-1", file);

        assertThat(response.profileImageUrl()).isEqualTo("new-url");
        verify(userRepository).save(user);
    }

    @Test
    void updateProfileImage_throwsIllegalArgument_whenUserNotFound() {
        when(userRepository.findByUserId("missing")).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("image", "avatar.png", "image/png", "bytes".getBytes());

        assertThatThrownBy(() -> userWriteService.updateProfileImageInternal("missing", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");

        verifyNoInteractions(cloudinaryService);
    }
    @Test
    void updateProfileImage_deletesOrphanedNewImage_whenDbSaveFails() {
        UserInfo user = UserInfo.builder()
                .userId("user-1")
                .profileImagePublicId("old-public-id")
                .build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile(
                "image", "avatar.png", "image/png", "bytes".getBytes());
        when(cloudinaryService.upload(file)).thenReturn(
                Map.of("secure_url", "new-url", "public_id", "new-public-id"));
        when(userRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> userWriteService.updateProfileImageInternal("user-1", file))
                .isInstanceOf(RuntimeException.class);


        verify(cloudinaryService).delete("new-public-id");
        verify(cloudinaryService, never()).delete("old-public-id");
    }

}
