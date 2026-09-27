package sagar.userService.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import sagar.userService.UserLockManager;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.dtos.request.CreateUserRequest;
import sagar.userService.dtos.request.UpdateUserRequest;
import sagar.userService.dtos.response.UserProfileImageResponse;
import sagar.userService.dtos.response.UserResponse;
import sagar.userService.entities.UserInfo;
import sagar.userService.producer.UserDeletedProducer;
import sagar.userService.repository.UserRepository;

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
    void createUser_acquiresLockOnUserId_andDelegatesToWriteService() {
        stubLockManagerToExecuteAction();
        CreateUserRequest dto = CreateUserRequest.builder().userId("user-1").email("a@b.com").build();
        UserResponse response = UserResponse.builder().userId("user-1").email("a@b.com").build();

        when(userWriteService.createUserInternal(dto)).thenReturn(response);

        UserResponse result = userService.createUser(dto);

        assertThat(result.getUserId()).isEqualTo("user-1");
        verify(userLockManager).withUserLock(eq("user-1"), any(Supplier.class));
        verify(userWriteService).createUserInternal(dto);
    }

    @Test
    void updateUser_acquiresLockOnUserId_andDelegatesToWriteService() {
        stubLockManagerToExecuteAction();
        UpdateUserRequest dto = UpdateUserRequest.builder().email("a@b.com").build();
       UserResponse response = UserResponse.builder().userId("user-1").email("a@b.com").build();

        when(userWriteService.updateUserInternal("user-1", dto)).thenReturn(response);

        UserResponse result = userService.updateUser("user-1", dto);

        assertThat(result.getUserId()).isEqualTo("user-1");
        verify(userLockManager).withUserLock(eq("user-1"), any(Supplier.class));
        verify(userWriteService).updateUserInternal("user-1", dto);
    }

    @Test
    void upsertFromEvent_acquiresLockOnUserId_andDelegatesToWriteService() {
        stubLockManagerToExecuteAction();
        UserInfoDto dto = UserInfoDto.builder().userId("user-1").email("a@b.com").build();
        when(userWriteService.createOrUpdateUserInternal(dto)).thenReturn(dto);

        UserInfoDto result = userService.upsertFromEvent(dto);

        verify(userLockManager).withUserLock(eq("user-1"), any(Supplier.class));
        verify(userWriteService).createOrUpdateUserInternal(dto);
    }


    @Test
    void getUserByUserId_returnsDto_whenUserExists() {
        UserInfo entity = UserInfo.builder().userId("user-1").email("a@b.com").build();
        when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(entity));

        UserResponse result = userService.getUserByUserId("user-1");

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

        UserResponse result = userService.getUserByEmail("a@b.com");

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
    void updateProfileImage_acquiresLock_andDelegatesToWriteService() {
        stubLockManagerToExecuteAction();

        MockMultipartFile file = new MockMultipartFile(
                "image",
                "avatar.png",
                "image/png",
                "bytes".getBytes()
        );

        UserProfileImageResponse expected =
                new UserProfileImageResponse("user-1", "new-url");

        when(userWriteService.updateProfileImageInternal("user-1", file))
                .thenReturn(expected);

        UserProfileImageResponse result =
                userService.updateProfileImage("user-1", file);

        assertThat(result.profileImageUrl()).isEqualTo("new-url");

        verify(userLockManager)
                .withUserLock(eq("user-1"), any(Supplier.class));

        verify(userWriteService)
                .updateProfileImageInternal("user-1", file);
    }



}
