package sagar.userService.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sagar.userService.dtos.UserDeletedEvent;
import sagar.userService.UserLockManager;
import sagar.userService.producer.UserDeletedProducer;
import sagar.userService.entities.UserInfo;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.dtos.UserProfileImageResponse;
import sagar.userService.repository.UserRepository;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserLockManager userLockManager;
    private final CloudinaryService cloudinaryService;
    private final UserDeletedProducer userDeletedProducer;
    private final UserWriteService userWriteService;

    public UserInfoDto createOrUpdateUser(UserInfoDto userInfoDto) {
        return userLockManager.withUserLock(userInfoDto.getUserId(),
                () -> userWriteService.createOrUpdateUserInternal(userInfoDto));
    }


    public UserInfoDto getUserByUserId(String userId) {
        log.debug("Fetching user by userId: {}", userId);

        UserInfo userInfo = userRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("User not found with userId: {}", userId);
                    return new IllegalArgumentException("User not found with userId: " + userId);
                });

        return UserInfoDto.fromEntity(userInfo);
    }

    public UserInfoDto getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);

        UserInfo userInfo = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", email);
                    return new IllegalArgumentException("User not found with email: " + email);
                });

        return UserInfoDto.fromEntity(userInfo);
    }

    public void deleteUser(String userId) {
        userLockManager.withUserLock(userId, () -> userWriteService.deleteUserInternal(userId));
    }



    public UserProfileImageResponse updateProfileImage(String userId, MultipartFile file) {
        UserInfo user = userRepository.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("User not found with userId: " + userId));

        String oldPublicId = user.getProfileImagePublicId();

        // Upload FIRST — if this throws, the user keeps their old image
        Map<String, Object> result = cloudinaryService.upload(file);

        user.setProfilePic((String) result.get("secure_url"));
        user.setProfileImagePublicId((String) result.get("public_id"));
        userRepository.save(user);

        // Only delete the old one after the new one is safely persisted
        if (oldPublicId != null) {
            try {
                cloudinaryService.delete(oldPublicId);
            } catch (Exception e) {
                // don't fail the request over cleanup — just log it for a later sweep
                log.warn("Failed to delete old profile image {} for user {}", oldPublicId, userId, e);
            }
        }

        return new UserProfileImageResponse(user.getUserId(), user.getProfilePic());
    }
}
