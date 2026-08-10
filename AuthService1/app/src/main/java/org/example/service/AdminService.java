package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.exceptions.DuplicateResourceException;
import org.example.exceptions.ResourceNotFoundException;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminService {
    private final UserRepository userRepository;
    // + OutboxRepository or an AuditLogRepository, whichever you go with

    public void promoteToAdmin(String userId) {
        UserInfo user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.getRoles().contains(UserRole.ROLE_ADMIN)) {
            throw new DuplicateResourceException("User is already an admin");
        }
        user.getRoles().add(UserRole.ROLE_ADMIN);
        userRepository.save(user);
        log.info("User {} promoted to admin", userId);
        // TODO: emit audit event
    }

    public void demoteAdmin(String userId) {
        UserInfo user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.getRoles().remove(UserRole.ROLE_ADMIN);
        userRepository.save(user);
        log.info("User {} demoted by admin", userId);
        //also deleting refresh token of the admin
    }
}
