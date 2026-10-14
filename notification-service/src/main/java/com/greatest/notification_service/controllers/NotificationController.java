package com.greatest.notification_service.controllers;

import com.greatest.notification_service.dtos.NotificationDto;
import com.greatest.notification_service.dtos.RegisterTokenRequest;
import com.greatest.notification_service.services.NotificationService;
import com.greatest.notification_service.services.PushTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final PushTokenService pushTokenService;
    private final NotificationService notificationService;

    @PostMapping("/register-token")
    public ResponseEntity<Void> registerToken(Authentication authentication,
                                              @Valid @RequestBody RegisterTokenRequest request) {
        String userId = authentication.getName();
        log.info("Registering push token for userId: {}", userId);
        pushTokenService.saveToken(userId, request.getExpoToken());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Page<NotificationDto>> getNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        String userId = authentication.getName();
        log.info("GET /api/v1/notifications - userId={}, page={}, size={}", userId, page, size);
        return ResponseEntity.ok(notificationService.getNotifications(userId, page, size));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id, Authentication authentication) {
        String userId = authentication.getName();
        log.info("PATCH /api/v1/notifications/{}/read - userId={}", id, userId);
        notificationService.markRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> unreadCount(Authentication authentication) {
        String userId = authentication.getName();
        log.info("GET /api/v1/notifications/unread-count - userId={}", userId);
        return ResponseEntity.ok(notificationService.unreadCount(userId));
    }


    @DeleteMapping("/token")
    public   ResponseEntity<Void> deleteToken(Authentication authentication) {
        String userId = authentication.getName();
        log.info("Unregistering push token for userId: {}", userId);
        pushTokenService.deleteToken(userId);
        return ResponseEntity.noContent().build();
    }
}