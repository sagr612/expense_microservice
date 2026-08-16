package sagar.userService.services;

import jakarta.validation.Valid;
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


    public UserInfoDto updateUser(String userId, @Valid UserInfoDto dto) {
        return userLockManager.withUserLock(dto.getUserId(),
                () -> userWriteService.updateUserInternal(userId, dto));
    }

    public UserInfoDto createUser(@Valid UserInfoDto dto) {
        return userLockManager.withUserLock(dto.getUserId(),
                () -> userWriteService.createUserInternal(dto));
    }
    /**
     * Upsert used by the Kafka consumer only.
     * Kafka events use at-least-once delivery, so we need one operation that
     * handles both "create if new" and "update if exists" atomically under the
     * per-user lock. HTTP entry points instead use the strict createUser/updateUser
     * split so misuse (e.g. POST to an existing userId) fails loudly.
     */
    public UserInfoDto upsertFromEvent(UserInfoDto dto) {
        return userLockManager.withUserLock(dto.getUserId(),
                () -> userWriteService.createOrUpdateUserInternal(dto));
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
        return userLockManager.withUserLock(userId, () -> updateProfileImageInternal(userId, file));
    }

    private UserProfileImageResponse updateProfileImageInternal(String userId, MultipartFile file) {
        UserInfo user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with userId: " + userId));

        String oldPublicId = user.getProfileImagePublicId();

        // Upload FIRST — if this throws, the user keeps their old image
        Map<String, Object> result = cloudinaryService.upload(file);
        String newPublicId = (String) result.get("public_id");
        String newUrl = (String) result.get("secure_url");

        try {
            user.setProfilePic(newUrl);
            user.setProfileImagePublicId(newPublicId);
            userRepository.save(user);
        } catch (RuntimeException dbEx) {
            // DB save failed → the just-uploaded new image is now orphaned in Cloudinary.

            log.warn("DB save failed after Cloudinary upload; compensating by deleting orphan {}",
                    newPublicId, dbEx);
            try {
                cloudinaryService.delete(newPublicId);
            } catch (Exception cleanupEx) {
                log.error("Orphan cleanup ALSO failed for {}. Manual cleanup needed.",
                        newPublicId, cleanupEx);
            }
            throw dbEx;
        }

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
