package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import javax.management.relation.Role;

@RestController
@RequestMapping("/auth/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    @PostMapping("/users/{userId}/promote")
    public ResponseEntity<String> promoteToAdmin(@PathVariable String userId) {

        UserInfo user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found: " + userId));

        if (user.getRoles().contains(UserRole.ROLE_ADMIN)) {
            return ResponseEntity.ok("User is already an admin");
        }

        user.getRoles().add(UserRole.ROLE_ADMIN);

        userRepository.save(user);

        return ResponseEntity.ok(
                "User promoted to admin successfully");
    }

    @DeleteMapping("/users/{userId}/admin")
    public ResponseEntity<String> removeAdminRole(
            @PathVariable String userId) {

        UserInfo user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found: " + userId));

        user.getRoles().remove(UserRole.ROLE_ADMIN);

        userRepository.save(user);

        return ResponseEntity.ok(
                "Admin role removed successfully");
    }
}