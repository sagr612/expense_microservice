package org.example.services;

import org.example.entities.UserInfo;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.example.service.UserCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCleanupServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserCleanupService userCleanupService;

    private UserInfo user;

    @BeforeEach
    void setUp() {
        user = UserInfo.builder()
                .userId("user-1")
                .username("john")
                .email("john@test.com")
                .phoneNumber("9999999999")
                .build();
    }

    //---------------------------------------------------------
    // deleteUserData()
    //---------------------------------------------------------

    @Test
    void shouldDeleteUserDataSuccessfully() {
        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(user));
        userCleanupService.deleteUserData("user-1");
        verify(refreshTokenRepository)
                .deleteByUserInfo(user);
        verify(userRepository)
                .deleteByUserId("user-1");
    }

    @Test
    void shouldDoNothingWhenUserDoesNotExist() {

        when(userRepository.findById("user-1"))
                .thenReturn(Optional.empty());

        userCleanupService.deleteUserData("user-1");

        verify(refreshTokenRepository, never())
                .deleteByUserInfo(any());

        verify(userRepository, never())
                .deleteByUserId(anyString());
    }

    @Test
    void shouldDeleteRefreshTokenBeforeDeletingUser() {

        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(user));

        userCleanupService.deleteUserData("user-1");

        verify(refreshTokenRepository)
                .deleteByUserInfo(user);

        verify(userRepository)
                .deleteByUserId("user-1");

        verifyNoMoreInteractions(refreshTokenRepository);
    }

    @Test
    void shouldPropagateExceptionWhenRefreshTokenDeletionFails() {

        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(user));

        doThrow(new RuntimeException("Database Error"))
                .when(refreshTokenRepository)
                .deleteByUserInfo(user);

        assertThrows(RuntimeException.class,
                () -> userCleanupService.deleteUserData("user-1"));

        verify(userRepository, never())
                .deleteByUserId(anyString());
    }

    @Test
    void shouldPropagateExceptionWhenUserDeletionFails() {

        when(userRepository.findById("user-1"))
                .thenReturn(Optional.of(user));

        doThrow(new RuntimeException("Database Error"))
                .when(userRepository)
                .deleteByUserId("user-1");

        assertThrows(RuntimeException.class,
                () -> userCleanupService.deleteUserData("user-1"));

        verify(refreshTokenRepository)
                .deleteByUserInfo(user);

        verify(userRepository)
                .deleteByUserId("user-1");
    }

}