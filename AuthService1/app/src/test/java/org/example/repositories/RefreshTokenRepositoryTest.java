package org.example.repositories;

import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
public class RefreshTokenRepositoryTest {


    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private UserInfo user;
    private RefreshToken refreshToken;

    @BeforeEach
    void setup() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        user = UserInfo.builder()
                .userId("user-1")
                .username("john")
                .password("password")
                .roles(Set.of(UserRole.ROLE_USER))
                .phoneNumber("9999999999")
                .email("john@test.com")
                .build();
        userRepository.save(user);

        refreshToken = RefreshToken.builder()
                .tokenHash("sample-refresh-token")
                .userInfo(user)
                .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    //---------------------------------------------------------
    // findByTokenHash
    //---------------------------------------------------------

    @Test
    void shouldFindRefreshTokenByToken() {

        Optional<RefreshToken> result = refreshTokenRepository.findByTokenHash("sample-refresh-token");

        assertTrue(result.isPresent());
        assertEquals("sample-refresh-token", result.get().getTokenHash());
        assertEquals(user.getUserId(), result.get().getUserInfo().getUserId());
    }

    @Test
    void shouldReturnEmptyWhenTokenDoesNotExist() {

        Optional<RefreshToken> result = refreshTokenRepository.findByTokenHash("nonexistent-token");

        assertTrue(result.isEmpty());
    }

    //---------------------------------------------------------
    // findByUserInfo
    //---------------------------------------------------------

    @Test
    void shouldFindRefreshTokenByUserInfo() {

        List<RefreshToken> result = refreshTokenRepository.findByUserInfo(user);

        assertFalse(result.isEmpty());
        assertEquals("sample-refresh-token", result.get(0).getTokenHash());
    }

    @Test
    void shouldReturnEmptyWhenUserInfoHasNoRefreshToken() {

        UserInfo otherUser = UserInfo.builder()
                .userId("user-2")
                .username("jane")
                .password("password")
                .roles(Set.of(UserRole.ROLE_USER))
                .phoneNumber("8888888888")
                .email("jane@test.com")
                .build();
        userRepository.save(otherUser);

        List<RefreshToken> result = refreshTokenRepository.findByUserInfo(otherUser);

        assertTrue(result.isEmpty());
    }

    //---------------------------------------------------------
    // deleteByUserInfo
    //---------------------------------------------------------

    @Test
    void shouldDeleteRefreshTokenByUserInfo() {
        refreshTokenRepository.deleteByUserInfo(user);
        Optional<RefreshToken> result = refreshTokenRepository.findByTokenHash("sample-refresh-token");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldDoNothingWhenDeletingByUserInfoWithNoToken() {

        UserInfo otherUser = UserInfo.builder()
                .userId("user-2")
                .username("jane")
                .password("password")
                .roles(Set.of(UserRole.ROLE_USER))
                .phoneNumber("8888888888")
                .email("jane@test.com")
                .build();
        userRepository.save(otherUser);

        refreshTokenRepository.deleteByUserInfo(otherUser);

        // original token should be untouched
        assertTrue(refreshTokenRepository.findByTokenHash("sample-refresh-token").isPresent());
    }

    //---------------------------------------------------------
    // deleteByExpiryDateBefore
    //---------------------------------------------------------

    @Test
    void shouldDeleteExpiredRefreshTokens() {

        RefreshToken expired = RefreshToken.builder()
                .tokenHash("expired-token")
                .userInfo(user)
                .expiryDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        UserInfo secondUser = UserInfo.builder()
                .userId("user-2")
                .username("jane")
                .password("password")
                .roles(Set.of(UserRole.ROLE_USER))
                .phoneNumber("8888888888")
                .email("jane@test.com")
                .build();
        userRepository.save(secondUser);
        expired.setUserInfo(secondUser);
        refreshTokenRepository.save(expired);
        refreshTokenRepository.deleteByExpiryDateBefore(Instant.now());
        assertTrue(refreshTokenRepository.findByTokenHash("expired-token").isEmpty());
        assertTrue(refreshTokenRepository.findByTokenHash("sample-refresh-token").isPresent());
    }

    @Test
    void shouldNotDeleteUnexpiredRefreshTokens() {
        refreshTokenRepository.deleteByExpiryDateBefore(Instant.now().minus(1, ChronoUnit.DAYS));
        assertTrue(refreshTokenRepository.findByTokenHash("sample-refresh-token").isPresent());
    }
}

