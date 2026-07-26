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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class RefreshTokenRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("testdb")
                    .withUsername("postgres")
                    .withPassword("123");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
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
                .phoneNumber(9999999999L)
                .email("john@test.com")
                .build();
        userRepository.save(user);

        refreshToken = RefreshToken.builder()
                .token("sample-refresh-token")
                .userInfo(user)
                .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    //---------------------------------------------------------
    // findByToken
    //---------------------------------------------------------

    @Test
    void shouldFindRefreshTokenByToken() {

        Optional<RefreshToken> result = refreshTokenRepository.findByToken("sample-refresh-token");

        assertTrue(result.isPresent());
        assertEquals("sample-refresh-token", result.get().getToken());
        assertEquals(user.getUserId(), result.get().getUserInfo().getUserId());
    }

    @Test
    void shouldReturnEmptyWhenTokenDoesNotExist() {

        Optional<RefreshToken> result = refreshTokenRepository.findByToken("nonexistent-token");

        assertTrue(result.isEmpty());
    }

    //---------------------------------------------------------
    // findByUserInfo
    //---------------------------------------------------------

    @Test
    void shouldFindRefreshTokenByUserInfo() {

        Optional<RefreshToken> result = refreshTokenRepository.findByUserInfo(user);

        assertTrue(result.isPresent());
        assertEquals("sample-refresh-token", result.get().getToken());
    }

    @Test
    void shouldReturnEmptyWhenUserInfoHasNoRefreshToken() {

        UserInfo otherUser = UserInfo.builder()
                .userId("user-2")
                .username("jane")
                .password("password")
                .roles(Set.of(UserRole.ROLE_USER))
                .phoneNumber(8888888888L)
                .email("jane@test.com")
                .build();
        userRepository.save(otherUser);

        Optional<RefreshToken> result = refreshTokenRepository.findByUserInfo(otherUser);

        assertTrue(result.isEmpty());
    }

    //---------------------------------------------------------
    // deleteByUserInfo
    //---------------------------------------------------------

    @Test
    void shouldDeleteRefreshTokenByUserInfo() {
        refreshTokenRepository.deleteByUserInfo(user);
        Optional<RefreshToken> result = refreshTokenRepository.findByToken("sample-refresh-token");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldDoNothingWhenDeletingByUserInfoWithNoToken() {

        UserInfo otherUser = UserInfo.builder()
                .userId("user-2")
                .username("jane")
                .password("password")
                .roles(Set.of(UserRole.ROLE_USER))
                .phoneNumber(8888888888L)
                .email("jane@test.com")
                .build();
        userRepository.save(otherUser);

        refreshTokenRepository.deleteByUserInfo(otherUser);

        // original token should be untouched
        assertTrue(refreshTokenRepository.findByToken("sample-refresh-token").isPresent());
    }

    //---------------------------------------------------------
    // deleteByExpiryDateBefore
    //---------------------------------------------------------

    @Test
    void shouldDeleteExpiredRefreshTokens() {

        RefreshToken expired = RefreshToken.builder()
                .token("expired-token")
                .userInfo(user)
                .expiryDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        UserInfo secondUser = UserInfo.builder()
                .userId("user-2")
                .username("jane")
                .password("password")
                .roles(Set.of(UserRole.ROLE_USER))
                .phoneNumber(8888888888L)
                .email("jane@test.com")
                .build();
        userRepository.save(secondUser);
        expired.setUserInfo(secondUser);
        refreshTokenRepository.save(expired);
        refreshTokenRepository.deleteByExpiryDateBefore(Instant.now());
        assertTrue(refreshTokenRepository.findByToken("expired-token").isEmpty());
        assertTrue(refreshTokenRepository.findByToken("sample-refresh-token").isPresent());
    }

    @Test
    void shouldNotDeleteUnexpiredRefreshTokens() {
        refreshTokenRepository.deleteByExpiryDateBefore(Instant.now().minus(1, ChronoUnit.DAYS));
        assertTrue(refreshTokenRepository.findByToken("sample-refresh-token").isPresent());
    }
}

