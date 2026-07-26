package sagar.userService.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import sagar.userService.UserLockManager;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.dtos.UserProfileImageResponse;
import sagar.userService.entities.UserInfo;
import sagar.userService.producer.UserDeletedProducer;
import sagar.userService.repository.UserRepository;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserLockManager userLockManager;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private UserDeletedProducer userDeletedProducer;
    @Mock
    private UserWriteService userWriteService;

    @InjectMocks
    private UserService userService;

    // Helper: makes the mocked UserLockManager actually invoke the Supplier it's given,
    // rather than just recording the call. Mirrors what the real UserLockManager does
    // once the lock is acquired.
    @SuppressWarnings("unchecked")
    private void stubLockManagerToExecuteAction() {
        when(userLockManager.withUserLock(any(String.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<Object> action = invocation.getArgument(1);
                    return action.get();
                });
    }

    @Test
    void createOrUpdateUser_acquiresLockOnUserId_andDelegatesToWriteService() {
        stubLockManagerToExecuteAction();

        UserInfoDto dto = UserInfoDto.builder().userId("user-1").email("a@b.com").build();
        UserInfoDto saved = UserInfoDto.builder().userId("user-1").email("a@b.com").build();

        when(userWriteService.createOrUpdateUserInternal(dto)).thenReturn(saved);

        UserInfoDto result = userService.createOrUpdateUser(dto);

        assertThat(result.getUserId()).isEqualTo("user-1");
        verify(userLockManager).withUserLock(eq("user-1"), any(Supplier.class));
        verify(userWriteService).createOrUpdateUserInternal(dto);
    }

    @Test
    void getUserByUserId_returnsDto_whenUserExists() {
        UserInfo entity = UserInfo.builder().userId("user-1").email("a@b.com").build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(entity));

        UserInfoDto result = userService.getUserByUserId("user-1");

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getEmail()).isEqualTo("a@b.com");
    }

    @Test
    void getUserByUserId_throwsIllegalArgument_whenUserNotFound() {
        when(userRepository.findByUserId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByUserId("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void getUserByEmail_returnsDto_whenUserExists() {
        UserInfo entity = UserInfo.builder().userId("user-1").email("a@b.com").build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(entity));

        UserInfoDto result = userService.getUserByEmail("a@b.com");

        assertThat(result.getEmail()).isEqualTo("a@b.com");
    }

    @Test
    void getUserByEmail_throwsIllegalArgument_whenUserNotFound() {
        when(userRepository.findByEmail("missing@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByEmail("missing@b.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing@b.com");
    }

    @Test
    void deleteUser_acquiresLockOnUserId_andDelegatesToWriteService() {
        // deleteUser uses the Runnable overload of withUserLock, not the Supplier one
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(userLockManager).withUserLock(any(String.class), any(Runnable.class));

        userService.deleteUser("user-1");

        verify(userLockManager).withUserLock(eq("user-1"), any(Runnable.class));
        verify(userWriteService).deleteUserInternal("user-1");
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

        UserProfileImageResponse response = userService.updateProfileImage("user-1", file);

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

        userService.updateProfileImage("user-1", file);

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

        assertThatThrownBy(() -> userService.updateProfileImage("user-1", file))
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
        UserProfileImageResponse response = userService.updateProfileImage("user-1", file);

        assertThat(response.profileImageUrl()).isEqualTo("new-url");
        verify(userRepository).save(user);
    }

    @Test
    void updateProfileImage_throwsIllegalArgument_whenUserNotFound() {
        when(userRepository.findByUserId("missing")).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("image", "avatar.png", "image/png", "bytes".getBytes());

        assertThatThrownBy(() -> userService.updateProfileImage("missing", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");

        verifyNoInteractions(cloudinaryService);
    }
}
