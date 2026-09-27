package org.example.integration;


import org.example.consumers.UserDeletedConsumer;
import org.example.entities.PasswordResetToken;
import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.model.UserDeletedEvent;
import org.example.repository.PasswordResetTokenRepository;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.example.service.UserCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {"spring.flyway.enabled=false"})
@ActiveProfiles("test")
class UserCleanupIntegrationTest {
    @Autowired
    private UserDeletedConsumer userDeletedConsumer;
    @Autowired
    private UserCleanupService userCleanupService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private UserInfo testUser;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        testUser = userRepository.save(UserInfo.builder()
                .userId(UUID.randomUUID().toString())
                .username("user_to_delete")
                .password("HashedPassword123")
                .email("user_to_delete@test.com")
                .phoneNumber("+12345678905")
                .roles(new HashSet<>(Set.of(UserRole.ROLE_USER)))
                .build());
    }

    @Test
    @DisplayName("UserDeletedEvent cascades deletion across UserInfo, RefreshTokens, and PasswordResetTokens")
    void deleteUserData_cascadesDeletionAcrossAllTables_successfully() {
        String userId = testUser.getUserId();

        // 1. Attach active refresh tokens
        RefreshToken token1 = refreshTokenRepository.save(RefreshToken.builder()
                .userInfo(testUser)
                .tokenHash("refresh-hash-1")
                .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());

        // 2. Attach password reset token
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(testUser);
        resetToken.setToken("reset-hash-1");
        resetToken.setUsed(false);
        resetToken.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(resetToken);

        // Precondition checks
        assertTrue(userRepository.findById(userId).isPresent());
        assertEquals(1, refreshTokenRepository.findByUserInfo(testUser).size());
        assertEquals(1, passwordResetTokenRepository.count());

        // 3. Simulate consumer receiving user.deleted event from Kafka
        UserDeletedEvent event = new UserDeletedEvent(userId);
        userDeletedConsumer.consume(event);

        // 4. Assert all traces of user are completely erased
        Optional<UserInfo> deletedUser = userRepository.findById(userId);
        assertTrue(deletedUser.isEmpty(), "UserInfo must be deleted");
        List<RefreshToken> remainingTokens = refreshTokenRepository.findByUserInfo(testUser);
        assertTrue(remainingTokens.isEmpty(), "All refresh tokens must be cascade-deleted");
        assertEquals(0, passwordResetTokenRepository.count(), "All password reset tokens must be cascade-deleted");
    }

    @Test
    @DisplayName("Idempotency: duplicate UserDeletedEvent for same user runs cleanly without error")
    void deleteUserData_isIdempotent_whenEventReceivedMultipleTimes() {
        String userId = testUser.getUserId();
        UserDeletedEvent event = new UserDeletedEvent(userId);

        // first
        assertDoesNotThrow(() -> userDeletedConsumer.consume(event));
        assertTrue(userRepository.findById(userId).isEmpty());

        // second
        assertDoesNotThrow(() -> userDeletedConsumer.consume(event), "Duplicate deletion events must be handled idempotently without throwing exceptions");
    }

    @Test
    @DisplayName("Ghost User: event for non-existent userId exits gracefully")
    void deleteUserData_handlesNonExistentUser_gracefully() {
        String nonExistentUserId = UUID.randomUUID().toString();
        UserDeletedEvent event = new UserDeletedEvent(nonExistentUserId);
        assertDoesNotThrow(() -> userDeletedConsumer.consume(event));
        assertTrue(userRepository.findById(testUser.getUserId()).isPresent());
    }


    @Test
    @DisplayName("Housekeeping: cleanupExpiredPasswordTokens purges expired tokens and retains active tokens")
    void cleanupExpiredPasswordTokens_deletesOnlyExpiredTokens() {
        // 1. Expired token (2 hours ago)
        PasswordResetToken expiredToken = new PasswordResetToken();
        expiredToken.setUser(testUser);
        expiredToken.setToken("expired-hash-val");
        expiredToken.setUsed(false);
        expiredToken.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
        passwordResetTokenRepository.save(expiredToken);

        // 2. Active unexpired token (15 mins in future)
        // Note: PasswordResetToken has a 1-to-1 or unique relation per user depending on DB constraint,
        // so we create a second user for the active token to avoid collision
        UserInfo secondUser = userRepository.save(UserInfo.builder()
                .userId(UUID.randomUUID().toString())
                .username("active_user")
                .password("Password123")
                .email("active@test.com")
                .phoneNumber("+12345678906")
                .roles(new HashSet<>(Set.of(UserRole.ROLE_USER)))
                .build());
        PasswordResetToken activeToken = new PasswordResetToken();
        activeToken.setUser(secondUser);
        activeToken.setToken("active-hash-val");
        activeToken.setUsed(false);
        activeToken.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(activeToken);
        assertEquals(2, passwordResetTokenRepository.count(), "Should have 2 tokens before cleanup");

        userCleanupService.cleanupExpiredPasswordTokens();

        assertEquals(1, passwordResetTokenRepository.count(), "Only active token should remain");
        assertTrue(passwordResetTokenRepository.findByToken("expired-hash-val").isEmpty(),
                "Expired token must be deleted");
        assertTrue(passwordResetTokenRepository.findByToken("active-hash-val").isPresent(),
                "Active token must be retained");
    }
}
