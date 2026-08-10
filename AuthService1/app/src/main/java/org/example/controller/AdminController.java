package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

private final AdminService adminService;
    @PostMapping("/users/{userId}/promote")
    public ResponseEntity<Void> promote(@PathVariable String userId) {
        adminService.promoteToAdmin(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}/admin")
    public ResponseEntity<Void> demote(@PathVariable String userId) {
        adminService.demoteAdmin(userId);
        return ResponseEntity.noContent().build();
    }

}