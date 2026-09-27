package sagar.userService.services;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sagar.userService.UserLockManager;
import sagar.userService.dtos.request.CreateUserRequest;
import sagar.userService.dtos.request.UpdateUserRequest;
import sagar.userService.dtos.response.UserResponse;
import sagar.userService.producer.UserDeletedProducer;
import sagar.userService.entities.UserInfo;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.dtos.response.UserProfileImageResponse;
import sagar.userService.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserLockManager userLockManager;
    private final UserWriteService userWriteService;


    public UserResponse updateUser(String userId, @Valid UpdateUserRequest dto) {
        return userLockManager.withUserLock(userId,
                () -> userWriteService.updateUserInternal(userId, dto));
    }

    public UserResponse createUser(@Valid CreateUserRequest request) {
        return userLockManager.withUserLock(request.getUserId(),
                () -> {
                    return userWriteService.createUserInternal(request);
                });
    }

    public UserInfoDto upsertFromEvent(UserInfoDto dto) {
        return userLockManager.withUserLock(dto.getUserId(),
                () -> userWriteService.createOrUpdateUserInternal(dto));
    }

    public UserResponse getUserByUserId(String userId) {
        log.debug("Fetching user by userId: {}", userId);

        UserInfo userInfo = userRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("User not found with userId: {}", userId);
                    return new IllegalArgumentException("User not found with userId: " + userId);
                });

        return UserResponse.fromEntity(userInfo);
    }

    public UserResponse getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);

        UserInfo userInfo = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", email);
                    return new IllegalArgumentException("User not found with email: " + email);
                });

        return UserResponse.fromEntity(userInfo);
    }

    public void deleteUser(String userId) {
        userLockManager.withUserLock(userId, () -> userWriteService.deleteUserInternal(userId));
    }


    public UserProfileImageResponse updateProfileImage(String userId, MultipartFile file) {
        return userLockManager.withUserLock(userId, () -> userWriteService.updateProfileImageInternal(userId, file));
    }

}
