package org.example.services;

import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.exceptions.TokenExpiredException;
import org.example.model.RefreshTokenResponse;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.example.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpiration", 604800000L);
    }

    @Test
    void shouldCreateRefreshToken() {
        UserInfo user = UserInfo.builder().userId("1").username("john").build();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        RefreshTokenResponse token = refreshTokenService.createRefreshToken("john");
        assertNotNull(token);
        assertNotNull(token.getToken());
        verify(userRepository).findByUsername("john");

        verify(refreshTokenRepository).save(any());
    }

    @Test
    void shouldNotDeleteExistingRefreshTokenOnNewLogin() {
        UserInfo user = UserInfo.builder().userId("1").username("john").build();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        refreshTokenService.createRefreshToken("john");

        verify(refreshTokenRepository, never()).deleteByUserInfo(any());
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.empty());
        assertThrows(
                UsernameNotFoundException.class,
                () -> refreshTokenService.createRefreshToken("john")
        );
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldFindRefreshTokenByToken() throws Exception {
        String rawToken = "abc";
        String tokenHash = sha256Hex(rawToken);   // helper method

        RefreshToken token = RefreshToken.builder().tokenHash(tokenHash).build();
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        Optional<RefreshToken> result = refreshTokenService.findByToken(rawToken);

        assertTrue(result.isPresent());
        assertEquals(tokenHash, result.get().getTokenHash());
    }

    // Add this helper at the bottom of the class:
    private static String sha256Hex(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(hashBytes);
    }

    @Test
    void shouldReturnValidTokenWhenNotExpired() {

        RefreshToken token = RefreshToken.builder()
                        .expiryDate(Instant.now().plusSeconds(500))
                        .build();

        RefreshToken result = refreshTokenService.verifyExpirationAndDeleteIfExpired(token);
        assertEquals(token, result);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void shouldThrowExceptionWhenRefreshTokenExpired() {

        RefreshToken token = RefreshToken.builder()
                        .expiryDate(Instant.now().minusSeconds(10))
                        .build();

        assertThrows(
                TokenExpiredException.class,
                () -> refreshTokenService.verifyExpirationAndDeleteIfExpired(token)
        );

        verify(refreshTokenRepository).delete(token);
    }

    @Test
    void shouldCleanupExpiredTokens() {

        refreshTokenService.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteByExpiryDateBefore(any(Instant.class));
    }

    @Test
    void shouldGenerateDifferentRefreshTokens() {

        UserInfo user = UserInfo.builder()
                .userId("1")
                .username("john")
                .build();

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(user));

        when(refreshTokenRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        RefreshTokenResponse t1 =
                refreshTokenService.createRefreshToken("john");

        RefreshTokenResponse t2 =
                refreshTokenService.createRefreshToken("john");

        assertNotEquals(
                t1.getToken(),
                t2.getToken()
        );
    }

    @Test
    void shouldSetFutureExpiryDate() {

        UserInfo user = UserInfo.builder()
                .userId("1")
                .username("john")
                .build();

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(user));


        when(refreshTokenRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        RefreshTokenResponse token =
                refreshTokenService.createRefreshToken("john");

        assertTrue(
                token.getExpiryDate().isAfter(Instant.now())
        );
    }
}