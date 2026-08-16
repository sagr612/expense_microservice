package sagar.userService.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sagar.userService.dtos.UserDeletedEvent;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.entities.UserInfo;
import sagar.userService.exceptions.DuplicateResourceException;
import sagar.userService.producer.UserDeletedProducer;
import sagar.userService.repository.OutboxRepository;
import sagar.userService.repository.UserRepository;

import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class UserWriteService {
    private final UserRepository userRepository;
    private final UserDomainService userDomainService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

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
    public UserInfoDto createUserInternal(UserInfoDto dto) {
        log.info("Creating user: {}", dto.getUserId());
        userDomainService.validateUserDto(dto);
        if (userRepository.findByUserId(dto.getUserId()).isPresent()) {
            throw new DuplicateResourceException("User already exists: " + dto.getUserId());
        }
        UserInfo entity = userDomainService.createNewUser(dto);
        UserInfo saved = userRepository.save(entity);
        log.info("User created: {}", saved.getUserId());
        return UserInfoDto.fromEntity(saved);
    }

    @Transactional
    public UserInfoDto updateUserInternal(String userId, UserInfoDto dto) {
        log.info("Updating user: {}", userId);
        UserInfo existing = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        // Ignore any userId in the body; path is source of truth.
        dto.setUserId(userId);
        userDomainService.validateUserDto(dto);
        UserInfo updated = userDomainService.updateExistingUser(existing, dto);
        UserInfo saved = userRepository.save(updated);
        log.info("User updated: {}", saved.getUserId());
        return UserInfoDto.fromEntity(saved);
    }



    @Transactional
    public void deleteUserInternal(String userId) {
        log.info("Deleting user: {}", userId);

        UserInfo userInfo = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with userId: " + userId));

        userRepository.delete(userInfo);
        log.info("User deleted successfully: {}", userId);
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId(userId)
                .eventType("USER_DELETED")
                .payload(toJson(new UserDeletedEvent(userId)))
                .published(false)
                .build();

        outboxRepository.save(event);
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
