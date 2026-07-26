package org.example.services;

import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.exceptions.TokenExpiredException;
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
        when(userRepository.findByUsername("john")).thenReturn(user);
        when(refreshTokenRepository.findByUserInfo(user)).thenReturn(Optional.empty());
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        RefreshToken token = refreshTokenService.createRefreshToken("john");
        assertNotNull(token);
        assertNotNull(token.getToken());
        assertEquals(user, token.getUserInfo());
        verify(userRepository).findByUsername("john");
        verify(refreshTokenRepository).deleteByUserInfo(user);
        verify(refreshTokenRepository).flush();
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void shouldDeleteExistingRefreshTokenBeforeCreatingNewOne() {
        UserInfo user = UserInfo.builder().userId("1").username("john").build();

        RefreshToken existing = RefreshToken.builder()
                        .token("old-token")
                        .userInfo(user)
                        .expiryDate(Instant.now().plusSeconds(100))
                        .build();

        when(userRepository.findByUsername("john"))
                .thenReturn(user);

        when(refreshTokenRepository.findByUserInfo(user))
                .thenReturn(Optional.of(existing));

        when(refreshTokenRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        refreshTokenService.createRefreshToken("john");

        verify(refreshTokenRepository).delete(existing);
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {

        when(userRepository.findByUsername("john"))
                .thenReturn(null);

        assertThrows(
                UsernameNotFoundException.class,
                () -> refreshTokenService.createRefreshToken("john")
        );

        verify(refreshTokenRepository, never())
                .save(any());
    }

    @Test
    void shouldFindRefreshTokenByToken() {

        RefreshToken token =
                RefreshToken.builder()
                        .token("abc")
                        .build();

        when(refreshTokenRepository.findByToken("abc"))
                .thenReturn(Optional.of(token));

        Optional<RefreshToken> result =
                refreshTokenService.findByToken("abc");

        assertTrue(result.isPresent());
        assertEquals("abc", result.get().getToken());
    }

    @Test
    void shouldReturnValidTokenWhenNotExpired() {

        RefreshToken token =
                RefreshToken.builder()
                        .expiryDate(Instant.now().plusSeconds(500))
                        .build();

        RefreshToken result =
                refreshTokenService.verifyExpiration(token);

        assertEquals(token, result);

        verify(refreshTokenRepository, never())
                .delete(any());
    }

    @Test
    void shouldThrowExceptionWhenRefreshTokenExpired() {

        RefreshToken token =
                RefreshToken.builder()
                        .expiryDate(Instant.now().minusSeconds(10))
                        .build();

        assertThrows(
                TokenExpiredException.class,
                () -> refreshTokenService.verifyExpiration(token)
        );

        verify(refreshTokenRepository)
                .delete(token);
    }

    @Test
    void shouldCleanupExpiredTokens() {

        refreshTokenService.cleanupExpiredTokens();

        verify(refreshTokenRepository)
                .deleteByExpiryDateBefore(any(Instant.class));
    }

    @Test
    void shouldGenerateDifferentRefreshTokens() {

        UserInfo user = UserInfo.builder()
                .userId("1")
                .username("john")
                .build();

        when(userRepository.findByUsername("john"))
                .thenReturn(user);

        when(refreshTokenRepository.findByUserInfo(user))
                .thenReturn(Optional.empty());

        when(refreshTokenRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        RefreshToken t1 =
                refreshTokenService.createRefreshToken("john");

        RefreshToken t2 =
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
                .thenReturn(user);

        when(refreshTokenRepository.findByUserInfo(user))
                .thenReturn(Optional.empty());

        when(refreshTokenRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        RefreshToken token =
                refreshTokenService.createRefreshToken("john");

        assertTrue(
                token.getExpiryDate().isAfter(Instant.now())
        );
    }
}