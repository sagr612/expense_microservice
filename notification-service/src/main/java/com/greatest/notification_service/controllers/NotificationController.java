package com.greatest.notification_service.controllers;

import com.greatest.notification_service.dtos.NotificationDto;
import com.greatest.notification_service.dtos.RegisterTokenRequest;
import com.greatest.notification_service.services.NotificationService;
import com.greatest.notification_service.services.PushTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<Void> registerToken(Authentication authentication, @RequestBody RegisterTokenRequest request) {
        String userId= authentication.getName();
        log.info("Registering push token for userId: {}", userId);
        pushTokenService.saveToken(userId, request.getExpoToken());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>>
    getNotifications(Authentication authentication) {
        String userId= authentication.getName();
        return ResponseEntity.ok(notificationService.getNotifications(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id,Authentication authentication) {
        String userId= authentication.getName();

        notificationService.markRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> unreadCount(Authentication authentication) {
        String userId= authentication.getName();

        return ResponseEntity.ok(notificationService.unreadCount(userId));
    }
}