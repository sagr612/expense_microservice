package sagar.userService.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sagar.userService.dtos.event.UserDeletedEvent;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.dtos.request.UpdateUserRequest;
import sagar.userService.dtos.response.UserProfileImageResponse;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.entities.UserInfo;
import sagar.userService.exceptions.DuplicateResourceException;
import sagar.userService.repository.OutboxRepository;
import sagar.userService.repository.UserRepository;
import sagar.userService.dtos.request.CreateUserRequest;
import sagar.userService.dtos.request.UpdateUserRequest;
import sagar.userService.dtos.response.UserResponse;


import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class UserWriteService {
    private final UserRepository userRepository;
    private final UserDomainService userDomainService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final CloudinaryService cloudinaryService;

    @Transactional
    public UserInfoDto createOrUpdateUserInternal(UserInfoDto userInfoDto) {
        log.info("Creating or updating user: {}", userInfoDto.getUserId());

        userDomainService.validateUserDto(userInfoDto);

        UserInfo userInfo = userRepository.findByUserId(userInfoDto.getUserId())
                .map(existingUser -> userDomainService.updateExistingUser(existingUser, userInfoDto))
                .orElseGet(() -> userDomainService.createNewUser(userInfoDto));

        UserInfo saved = userRepository.save(userInfo);
        log.info("User saved successfully: {}", saved.getUserId());

        return UserInfoDto.fromEntity(saved);
    }

    @Transactional
    public UserResponse createUserInternal(CreateUserRequest dto) {
        log.info("Creating user: {}", dto.getUserId());

        if (userRepository.findByUserId(dto.getUserId()).isPresent()) {
            throw new DuplicateResourceException("User already exists: " + dto.getUserId());
        }
        UserInfo entity = userDomainService.createNewUser(dto);
        UserInfo saved = userRepository.save(entity);
        log.info("User created: {}", saved.getUserId());
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public UserResponse updateUserInternal(String userId, UpdateUserRequest dto) {
        log.info("Updating user: {}", userId);
        UserInfo existing = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

//        userDomainService.validateUserDto(dto);
        UserInfo updated = userDomainService.updateExistingUser(existing, dto);
        UserInfo saved = userRepository.save(updated);
        log.info("User updated: {}", saved.getUserId());
        return UserResponse.fromEntity(saved);
    }


    @Transactional
    public void deleteUserInternal(String userId) {
        log.info("Deleting user: {}", userId);

        UserInfo userInfo = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with userId: " + userId));

        userRepository.delete(userInfo);
        log.info("User deleted successfully: {}", userId);
        UserDeletedEvent userDeletedEvent = UserDeletedEvent.builder()

                .userId(userId)
                .build();
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId(userId)
                .eventType("USER_DELETED")
                .payload(toJson(userDeletedEvent))
                .published(false)
                .build();

        outboxRepository.save(event);
    }

//    @Transactional
    public UserProfileImageResponse updateProfileImageInternal(String userId, MultipartFile file) {
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            // this should never realistically happen for a simple DTO like UserDeletedEvent,
            // but fail loudly rather than silently writing a broken outbox row
            throw new IllegalStateException("Failed to serialize event for outbox: " + obj, e);
        }
    }


}
