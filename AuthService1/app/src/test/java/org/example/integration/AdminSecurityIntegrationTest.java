package org.example.integration;

import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.repository.PasswordResetTokenRepository;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.example.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {"spring.flyway.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    private UserInfo adminUser;
    private UserInfo regularUser;
    private String adminJwt;
    private String regularUserJwt;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        adminUser = userRepository.save(UserInfo.builder()
                .userId(UUID.randomUUID().toString())
                .username("admin_ops")
                .password(passwordEncoder.encode("AdminPass123"))
                .email("admin@test.com")
                .phoneNumber("+12345678903")
                .roles(new HashSet<>(Set.of(UserRole.ROLE_USER, UserRole.ROLE_ADMIN)))
                .build());
        regularUser = userRepository.save(UserInfo.builder()
                .userId(UUID.randomUUID().toString())
                .username("regular_user")
                .password(passwordEncoder.encode("UserPass123"))
                .email("user@test.com")
                .phoneNumber("+12345678904")
                .roles(new HashSet<>(Set.of(UserRole.ROLE_USER)))
                .build());
        adminJwt = jwtService.generateToken(
                adminUser.getUsername(),
                adminUser.getUserId(),
                adminUser.getRoles().stream().map(UserRole::name).toList());
        regularUserJwt = jwtService.generateToken(
                regularUser.getUsername(),
                regularUser.getUserId(),
                regularUser.getRoles().stream().map(UserRole::name).toList());
    }

    @Test
    @DisplayName("Security Guard: Unauthenticated requests to admin endpoints are rejected")
    void unauthenticatedRequest_isRejected() throws Exception {
        mockMvc.perform(post("/auth/v1/admin/users/{userId}/promote", regularUser.getUserId()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Unauthenticated caller must be rejected with 401 or 403");
                });
    }

    @Test
    @DisplayName("RBAC Guard: Regular user with ROLE_USER is rejected with 403 Forbidden")
    void nonAdminUser_cannotAccessAdminEndpoints_returns403() throws Exception {
        mockMvc.perform(post("/auth/v1/admin/users/{userId}/promote", regularUser.getUserId())
                .header("Authorization", "Bearer " + regularUserJwt)
        ).andExpect(status().isForbidden());

        mockMvc.perform(post("/auth/v1/admin/users/{userId}/admin", regularUser.getUserId())
                .header("Authorization", "Bearer " + regularUserJwt)
        ).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin caller successfully promotes a user to ROLE_ADMIN")
    void promoteUser_addsAdminRoleSuccessfully() throws Exception {
        mockMvc.perform(post("/auth/v1/admin/users/{userId}/promote", regularUser.getUserId())
                .header("Authorization", "Bearer " + adminJwt)
        ).andExpect(status().isNoContent());

        UserInfo updated = userRepository.findById(regularUser.getUserId()).orElseThrow();
        assertTrue(updated.getRoles().contains(UserRole.ROLE_ADMIN), "User should now have ROLE_ADMIN");
    }

    @Test
    @DisplayName("Idempotency: Promoting an existing admin returns 409 Conflict")
    void promoteUser_alreadyAdmin_returnsConflict() throws Exception {
        mockMvc.perform(post("/auth/v1/admin/users/{userId}/promote", adminUser.getUserId())
                .header("Authorization", "Bearer " + adminJwt)
        ).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Demote admin removes ROLE_ADMIN and revokes all active sessions")
    void demoteAdmin_removesRoleAndRevokesAllSessions() throws Exception {
        // promote regular user to admin first
        regularUser.getRoles().add(UserRole.ROLE_ADMIN);
        userRepository.save(regularUser);

        // create a active jwt token session for this user
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .userInfo(regularUser)
                        .tokenHash("hash-regular-token")
                        .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                        .build()
        );
        assertEquals(1, refreshTokenRepository.findByUserInfo(regularUser).size(), "Session should exist");

        // admin demotes user
        mockMvc.perform(delete("/auth/v1/admin/users/{userId}/admin", regularUser.getUserId())
                .header("Authorization", "Bearer " + adminJwt)
        ).andExpect(status().isNoContent());

        // verify ROLE_ADMIN removed
        UserInfo demoted = userRepository.findById(regularUser.getUserId()).orElseThrow();
        assertFalse(demoted.getRoles().contains(UserRole.ROLE_ADMIN), "User should no longer have ROLE_ADMIN");

        // verify all all refreshTokens for demoted user are removed
        List<RefreshToken> remainingSessions = refreshTokenRepository.findByUserInfo(regularUser);
        assertTrue(remainingSessions.isEmpty(), "Remaining Sessions should be empty");
    }

    @Test
    @DisplayName("Operating on non-existent user returns 404 Not Found")
    void adminOperations_nonExistentUser_returnsNotFound() throws Exception {
        String randomUserId = UUID.randomUUID().toString();
        mockMvc.perform(post("/auth/v1/admin/users/{userId}/promote", randomUserId)
                .header("Authorization", "Bearer " + adminJwt)
        ).andExpect(status().isNotFound());

        mockMvc.perform(delete("/auth/v1/admin/users/{userId}/admin", randomUserId)
                .header("Authorization", "Bearer " + adminJwt)
        ).andExpect(status().isNotFound());

    }
}
