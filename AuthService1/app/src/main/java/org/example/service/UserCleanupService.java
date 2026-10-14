package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entities.UserInfo;
import org.example.repository.PasswordResetTokenRepository;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Transactional
    public void deleteUserData(String userId) {

        log.info("Deleting data for user {}", userId);

        Optional<UserInfo> user = userRepository.findById(userId);
        if (user.isEmpty()) return;

        refreshTokenRepository.deleteByUserInfo(user.get());
        passwordResetTokenRepository.deleteByUserId(userId);
        userRepository.deleteByUserId(userId);

        log.info("Successfully deleted all data for user {}", userId);
    }

    @Scheduled(cron = "0 0 3 * * ?") // Runs daily at 3 AM
    public void cleanupExpiredPasswordTokens() {
        log.info("Starting scheduled cleanup of expired password reset tokens");
        passwordResetTokenRepository.deleteExpiredTokens(Instant.now());
        log.info("Completed scheduled cleanup of expired password reset tokens");
    }

}