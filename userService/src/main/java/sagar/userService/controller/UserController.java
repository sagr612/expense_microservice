package sagar.userService.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.dtos.UserProfileImageResponse;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.services.OutboxAdminService;
import sagar.userService.services.UserService;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final  UserService userService;
    private final OutboxAdminService  outboxAdminService;


    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or #dto.userId == authentication.principal")
    public ResponseEntity<UserInfoDto> createUser(@RequestBody @Valid UserInfoDto dto) {
        log.info("POST /api/v1/users - userId: {}", dto.getUserId());
        UserInfoDto created = userService.createUser(dto);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + created.getUserId()))
                .body(created);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal")
    public ResponseEntity<UserInfoDto> updateUser(@PathVariable String userId,
                                                  @RequestBody @Valid UserInfoDto dto) {
        log.info("PUT /api/v1/users/{}", userId);
        UserInfoDto updated = userService.updateUser(userId, dto);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal")
    public ResponseEntity<UserInfoDto> getUser(@PathVariable String userId) {
        log.info("GET /api/v1/users/{}", userId);

        UserInfoDto user = userService.getUserByUserId(userId);

        return ResponseEntity.ok(user);
    }

    @GetMapping("/emails/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserInfoDto> getUserByEmail(@PathVariable String email) {
        log.info("GET /api/v1/users/emails/{}", email);

        UserInfoDto user = userService.getUserByEmail(email);

        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        log.info("DELETE /api/v1/users/{}", userId);

        userService.deleteUser(userId);

        log.info("User deleted successfully: {}", userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(value = "/{userId}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal")
    public ResponseEntity<UserProfileImageResponse> uploadProfileImage(@PathVariable String userId, @RequestParam("image") MultipartFile file) {

        validateImage(file);
        UserProfileImageResponse response = userService.updateProfileImage(userId, file);
        return ResponseEntity.ok(response);
    }

    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File must be an image");

        }
    }

    @PostMapping("/admin/outbox/{eventId}/revive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reviveDeadOutboxEvent(@PathVariable String eventId) {
        outboxAdminService.reviveEvent(eventId);
        return ResponseEntity.noContent().build();
    }

}
