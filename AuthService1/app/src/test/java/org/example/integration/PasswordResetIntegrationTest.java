package org.example.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entities.PasswordResetToken;
import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.model.ForgotPasswordRequest;
import org.example.model.ResetPasswordRequest;
import org.example.repository.PasswordResetTokenRepository;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.example.request.AuthRequestDTO;
import org.example.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = {"spring.flyway.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PasswordResetIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockBean
    private EmailService emailService;
    private UserInfo testUser;
    private final String initialPassword = "OldPassword123";
    private final String newPassword = "NewPassword456";

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        testUser = userRepository.save(UserInfo.builder()
                .userId(UUID.randomUUID().toString())
                .username("reset_tester")
                .password(passwordEncoder.encode(initialPassword))
                .email("reset_tester@example.com")
                .phoneNumber("+12345678902")
                .roles(new HashSet<>(Set.of(UserRole.ROLE_USER)))
                .build());
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String extractRawTokenFromEmail(String htmlBody) {
        int start = htmlBody.indexOf("token") + "token=".length();
        int end = htmlBody.indexOf("'>", start);
        return htmlBody.substring(start, end);
    }

    @Test
    @DisplayName("Complete Password Reset: updates password, hashes token in DB, and revokes all active sessions")
    void resetPassword_updatesPasswordAndRevokesAllSessions() throws Exception {
        // 1. Establish an active session before resetting password (simulate logged-in user on another device)
        refreshTokenRepository.save(RefreshToken.builder()
                .userInfo(testUser)
                .tokenHash(hashToken("active-session-token"))
                .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());
        assertEquals(1, refreshTokenRepository.findByUserInfo(testUser).size(), "User should have 1 active session");

        // 2. Request forgot-password
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest(testUser.getEmail());
        mockMvc.perform(post("/auth/v1/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset email sent"));


        // 3. Intercept the sent email to extract the raw token
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendEmail(eq(testUser.getEmail()), anyString(), bodyCaptor.capture());

        String rawResetToken = extractRawTokenFromEmail(bodyCaptor.getValue());
        assertNotNull(rawResetToken, "Raw token must be extracted from email link");

        // 4. Verify DB stores only the SHA-256 hash, not the raw token
        String expectedHash = hashToken(rawResetToken);
        Optional<PasswordResetToken> tokenEntity = passwordResetTokenRepository.findByToken(expectedHash);
        assertTrue(tokenEntity.isPresent(), "Hashed reset token must be present in DB");
        assertFalse(tokenEntity.get().isUsed(), "Token must be unused initially");

        // 5. Submit password reset with the new password
        ResetPasswordRequest resetReq = new ResetPasswordRequest(rawResetToken, newPassword);
        mockMvc.perform(post("/auth/v1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password updated successfully"));

        //  Verify token is marked used
        PasswordResetToken updatedToken = passwordResetTokenRepository.findByToken(expectedHash).orElseThrow();
        assertTrue(updatedToken.isUsed(), "Token must be marked as used");

        // Verify security: All active refresh tokens (sessions) were wiped upon password reset
        List<RefreshToken> activeSessions = refreshTokenRepository.findByUserInfo(testUser);
        assertTrue(activeSessions.isEmpty(), "All active user refresh tokens must be revoked after password reset");


        // Verify security: Old password fails, new password succeeds
        AuthRequestDTO oldLogin = AuthRequestDTO.builder().username(testUser.getUsername()).password(initialPassword).build();
        mockMvc.perform(post("/auth/v1/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(oldLogin)))
                .andExpect(status().isUnauthorized());

        AuthRequestDTO newLogin = AuthRequestDTO.builder().username(testUser.getUsername()).password(newPassword).build();
        mockMvc.perform(post("/auth/v1/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk());



    }


    @Test
    @DisplayName("Anti-Enumeration: requesting reset for non-existent email returns 200 without sending email")
    void forgotPassword_doesNotLeakNonExistentEmail() throws Exception {
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest("nonexistent@domain.com");
        mockMvc.perform(post("/auth/v1/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq))
                ).andExpect(status().isOk())
                .andExpect(content().string("Password reset email sent"));

        // ensure no email sent & no token was created
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        assertEquals(0, passwordResetTokenRepository.count());
    }

    @Test
    @DisplayName("Replay Attack Protection: using an already used token fails with 401 Unauthorized")
    void resetPassword_rejectsReusedToken() throws Exception {
        // Request reset and extract token
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest(testUser.getEmail());
        mockMvc.perform(post("/auth/v1/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk());
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq(testUser.getEmail()), anyString(), bodyCaptor.capture());
        String rawResetToken = extractRawTokenFromEmail(bodyCaptor.getValue());

        // first reset succeeds
        ResetPasswordRequest resetReq = new ResetPasswordRequest(rawResetToken, newPassword);
        mockMvc.perform(post("/auth/v1/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetReq))
        ).andExpect(status().isOk());

        // second reset with same token should be rejected
        ResetPasswordRequest resetReq2 = new ResetPasswordRequest(rawResetToken, "newPassword1231!");
        mockMvc.perform(post("/auth/v1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq2))
                ).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Reset token already used"));

    }

    @Test
    @DisplayName("Expiration: expired reset token fails with 401 Unauthorized")
    void resetPassword_rejectsExpiredToken() throws Exception {
        String rawExpiredToken = "rawExpiredToken123";
        String expiredTokenHash = hashToken(rawExpiredToken);
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(expiredTokenHash);
        resetToken.setUser(testUser);
        resetToken.setUsed(false);
        resetToken.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        passwordResetTokenRepository.save(resetToken);
        ResetPasswordRequest resetReq = new ResetPasswordRequest(rawExpiredToken, newPassword);

        mockMvc.perform(post("/auth/v1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq))
                ).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Reset token expired"));
    }

    @Test
    @DisplayName("Validation: weak password fails with 400 Bad Request")
    void resetPassword_rejectsWeakPassword() throws Exception {
        ResetPasswordRequest resetReq = new ResetPasswordRequest("raw-Token", "weak");

        mockMvc.perform(post("/auth/v1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq))
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));

    }
}
