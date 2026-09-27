package sagar.userService.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.dtos.request.CreateUserRequest;
import sagar.userService.dtos.request.UpdateUserRequest;
import sagar.userService.dtos.response.UserProfileImageResponse;
import sagar.userService.dtos.response.UserResponse;
import sagar.userService.services.ImageValidator;
import sagar.userService.services.OutboxAdminService;
import sagar.userService.services.UserService;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserService userService;
    private final OutboxAdminService outboxAdminService;
    private final ImageValidator validator;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or #request.userId == authentication.name")
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + created.getUserId()))
                .body(created);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.name")
    public ResponseEntity<UserResponse> updateUser(@PathVariable String userId,
                                                  @RequestBody @Valid UpdateUserRequest request) {
        UserResponse updated = userService.updateUser(userId, request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.name")
    public ResponseEntity<UserResponse> getUser(@PathVariable String userId) {

        UserResponse user = userService.getUserByUserId(userId);

        return ResponseEntity.ok(user);
    }

    @GetMapping("/emails/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email) {
        log.info("GET /api/v1/users/emails/{}", email);

        UserResponse user = userService.getUserByEmail(email);

        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.name")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        log.info("DELETE /api/v1/users/{}", userId);

        userService.deleteUser(userId);

        log.info("User deleted successfully: {}", userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(value = "/{userId}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.name")
    public ResponseEntity<UserProfileImageResponse> uploadProfileImage(@PathVariable String userId, @RequestParam("image") MultipartFile file) {

        validator.validate(file);
        UserProfileImageResponse response = userService.updateProfileImage(userId, file);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/admin/outbox/{eventId}/revive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reviveDeadOutboxEvent(@PathVariable String eventId) {
        outboxAdminService.reviveEvent(eventId);
        return ResponseEntity.noContent().build();
    }

}
