package sagar.userService.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.dtos.UserProfileImageResponse;
import sagar.userService.services.UserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final  UserService userService;

    @PostMapping
    public ResponseEntity<UserInfoDto> createUpdate(@RequestBody @Valid UserInfoDto userInfoDto) {
        log.info("POST /api/v1/users - userId: {}", userInfoDto.getUserId());

        UserInfoDto user = userService.createOrUpdateUser(userInfoDto);

        log.info("User created/updated successfully: {}", user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserInfoDto> getUser(@PathVariable String userId) {
        log.info("GET /api/v1/users/{}", userId);

        UserInfoDto user = userService.getUserByUserId(userId);

        return ResponseEntity.ok(user);
    }

    @GetMapping("/emails/{email}")
    public ResponseEntity<UserInfoDto> getUserByEmail(@PathVariable String email) {
        log.info("GET /api/v1/users/emails/{}", email);

        UserInfoDto user = userService.getUserByEmail(email);

        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        log.info("DELETE /api/v1/users/{}", userId);

        userService.deleteUser(userId);

        log.info("User deleted successfully: {}", userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(value = "/{userId}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
}
