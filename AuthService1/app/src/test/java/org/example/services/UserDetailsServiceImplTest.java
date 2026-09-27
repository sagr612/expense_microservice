package org.example.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entities.PasswordResetToken;
import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.eventProducer.UserInfoProducer;
import org.example.exceptions.InvalidTokenException;
import org.example.exceptions.TokenExpiredException;
import org.example.exceptions.UserAlreadyExistsException;
import org.example.model.PasswordDto;
import org.example.model.RefreshTokenResponse;
import org.example.model.UserInfoDto;
import org.example.repository.OutboxRepository;
import org.example.repository.PasswordResetTokenRepository;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.example.response.JwtResponseDTO;
import org.example.service.EmailService;
import org.example.service.JwtService;
import org.example.service.RefreshTokenService;
import org.example.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OutboxRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserDetailsServiceImpl service;

    private UserInfo user;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setup() {

        user = UserInfo.builder().userId("user-1").username("john").password("encodedPassword").phoneNumber("9999999999").email("john@test.com").roles(Set.of(UserRole.ROLE_USER)).build();
    }

    //=========================================================
    // loadUserByUsername()
    //=========================================================

    @Test
    void shouldLoadUserByUsername() {

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("john");

        assertNotNull(result);
        assertEquals("john", result.getUsername());
        assertEquals("encodedPassword", result.getPassword());

        verify(userRepository).findByUsername("john");
    }

    @Test
    void shouldThrowWhenUserNotFound() {

        when(userRepository.findByUsername("john")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("john"));

        verify(userRepository).findByUsername("john");
    }

    //=========================================================
    // checkIfUserAlreadyExist()
    //=========================================================

    @Test
    void shouldReturnExistingUser() {

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        UserInfo result = service.checkIfUserAlreadyExist("john");

        assertEquals(user, result);

        verify(userRepository).findByUsername("john");
    }

    //=========================================================
    // signupUser()
    //=========================================================

    @Test
    void shouldSignupSuccessfully() {

        UserInfoDto dto = UserInfoDto.builder().username("john").password("password123").firstName("John").lastName("Doe").email("john@test.com").phoneNumber("9999999999").build();

        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");

        when(userRepository.existsByUsername("john")).thenReturn(false);

        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        when(userRepository.save(any(UserInfo.class))).thenAnswer(i -> i.getArgument(0));

        UserInfo saved = service.signupUser(dto);

        assertNotNull(saved);

        assertEquals("john", saved.getUsername());

        assertEquals("encodedPassword", saved.getPassword());

        assertTrue(saved.getRoles().contains(UserRole.ROLE_USER));

        verify(passwordEncoder).encode("password123");

        verify(userRepository).save(any(UserInfo.class));


    }

    @Test
    void shouldGenerateRandomUserIdDuringSignup() {

        UserInfoDto dto = UserInfoDto.builder().username("john").password("password123").firstName("John").lastName("Doe").email("john@test.com").phoneNumber("9999999999").build();

        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        when(userRepository.save(any(UserInfo.class))).thenAnswer(i -> i.getArgument(0));

        UserInfo saved = service.signupUser(dto);

        assertNotNull(saved.getUserId());
        assertFalse(saved.getUserId().isBlank());
    }

    @Test
    void shouldThrowWhenUsernameAlreadyExists() {
        UserInfoDto dto = UserInfoDto.builder().username("john").password("password123").email("john@test.com").phoneNumber("9999999999").build();

        when(userRepository.existsByUsername("john")).thenReturn(true);

        UserAlreadyExistsException ex = assertThrows(UserAlreadyExistsException.class, () -> service.signupUser(dto));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPhoneAlreadyExists() {

        UserInfoDto dto = UserInfoDto.builder().username("john").password("password123").email("john@test.com").phoneNumber("9999999999").build();


//         when(userRepository.findByUsername(anyString())).thenReturn(null);
//        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> service.signupUser(dto));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenEmailAlreadyExists() {

        UserInfoDto dto = UserInfoDto.builder().username("john").password("password123").email("john@test.com").phoneNumber("9999999999").build();


//        when(userRepository.findByUsername(anyString())).thenReturn(null);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> service.signupUser(dto));
    }

    @Test
    void shouldPublishKafkaEventOnSignup() {

        UserInfoDto dto = UserInfoDto.builder().username("john").password("password123").firstName("John").lastName("Doe").email("john@test.com").phoneNumber("9999999999").build();

        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        when(userRepository.save(any(UserInfo.class))).thenAnswer(i -> i.getArgument(0));

        service.signupUser(dto);

    }

    @Test
    void shouldSaveEncodedPassword() {

        UserInfoDto dto = UserInfoDto.builder().username("john").password("password123").firstName("John").lastName("Doe").email("john@test.com").phoneNumber("9999999999").build();

        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        when(userRepository.save(any(UserInfo.class))).thenAnswer(i -> i.getArgument(0));

        service.signupUser(dto);

        ArgumentCaptor<UserInfo> captor = ArgumentCaptor.forClass(UserInfo.class);

        verify(userRepository).save(captor.capture());

        UserInfo saved = captor.getValue();

        assertEquals("encodedPassword", saved.getPassword());
    }
    //=========================================================
    // changePassword()
    //=========================================================

    @Test
    void shouldChangePasswordSuccessfully() {

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user-1", null));

        PasswordDto dto = PasswordDto.builder().oldPassword("oldPassword").newPassword("newPassword").build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        when(passwordEncoder.matches("oldPassword", "encodedPassword")).thenReturn(true);

        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");

        RefreshToken refreshToken = RefreshToken.builder().tokenHash("refresh-token").build();

        when(refreshTokenService.createRefreshToken("john")).thenReturn(RefreshTokenResponse.builder().token("refresh-token").build());

        when(jwtService.generateToken(anyString(), anyString(), anyList())).thenReturn("jwt-token");

        JwtResponseDTO response = service.changePassword(dto);

        assertNotNull(response);

        assertEquals("jwt-token", response.getAccessToken());

        assertEquals("refresh-token", response.getToken());

        assertEquals("newEncodedPassword", user.getPassword());

        verify(passwordEncoder).encode("newPassword");

        verify(refreshTokenService).createRefreshToken("john");

        verify(jwtService).generateToken(anyString(), anyString(), anyList());
    }

    @Test
    void shouldThrowWhenOldPasswordIncorrect() {

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user-1", null));

        PasswordDto dto = PasswordDto.builder().oldPassword("wrong").newPassword("newPassword").build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.changePassword(dto));

        assertEquals("Incorrect old password", exception.getMessage());

        verify(refreshTokenService, never()).createRefreshToken(anyString());

    }

    @Test
    void shouldThrowWhenNewPasswordSameAsOld() {

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user-1", null));

        PasswordDto dto = PasswordDto.builder().oldPassword("samePassword").newPassword("samePassword").build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.changePassword(dto));

        assertEquals("New password cannot be same as old password", exception.getMessage());

    }

    @Test
    void shouldThrowWhenChangingPasswordForMissingUser() {

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user-1", null));

        PasswordDto dto = PasswordDto.builder().oldPassword("old").newPassword("new").build();

        when(userRepository.findById("user-1")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.changePassword(dto));

    }

    //=========================================================
    // getUserByUsername()
    //=========================================================

    @Test
    void shouldReturnUserId() {

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        String id = service.getUserByUsername("john");

        assertEquals("user-1", id);

    }

    @Test
    void shouldReturnNullWhenUserDoesNotExist() {

        when(userRepository.findByUsername("john")).thenReturn(Optional.empty());

        assertNull(service.getUserByUsername("john"));

    }

    //=========================================================
    // changeUsername()
    //=========================================================

    @Test
    void shouldChangeUsernameSuccessfully() {

        when(userRepository.existsByUsername("newUser")).thenReturn(false);

        when(userRepository.findById("john")).thenReturn(Optional.of(user));

        when(refreshTokenService.createRefreshToken("newUser")).thenReturn(RefreshTokenResponse.builder().token("refresh-token").build());

        when(jwtService.generateToken(anyString(), anyString(), anyList())).thenReturn("jwt-token");

        JwtResponseDTO response = service.changeUsername("newUser", "john");

        assertEquals("newUser", user.getUsername());

        assertEquals("jwt-token", response.getAccessToken());

        assertEquals("refresh-token", response.getToken());

        verify(userRepository).save(user);

    }

    @Test
    void shouldThrowWhenUsernameAlreadyTaken() {
        when(userRepository.findById("john")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("newUser")).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.changeUsername("newUser", "john"));

        assertEquals("Username already taken", exception.getMessage());

        verify(userRepository, never()).save(any());

    }

    @Test
    void shouldThrowWhenCurrentUserDoesNotExist() {
        when(userRepository.findById("john")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.changeUsername("newUser", "john"));

        assertEquals("User not found", exception.getMessage());

    }

    //=========================================================
    // forgotPassword()
    //=========================================================

    @Test
    void shouldDoNothingWhenEmailIsUnknown() {
        when(userRepository.findByEmail("xyz@test.com")).thenReturn(Optional.empty());

        service.forgotPassword("xyz@test.com");

        verify(passwordResetTokenRepository, never()).save(any());

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void shouldGenerateTokenAndSendEmailWhenUserExists() {
        // 2. Existing email & Token creation
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));

        service.forgotPassword("john@test.com");
        verify(passwordResetTokenRepository).deleteByUserId(user.getUserId());

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);

        verify(passwordResetTokenRepository).save(captor.capture());

        PasswordResetToken savedToken = captor.getValue();

        assertNotNull(savedToken.getToken());
        assertEquals(64, savedToken.getToken().length());
        assertEquals(user, savedToken.getUser());
        assertFalse(savedToken.isUsed());
        assertTrue(savedToken.getExpiresAt().isAfter(Instant.now()));

        verify(emailService).sendEmail(eq(user.getEmail()), eq("Reset Your Password"), contains("expensetracker://reset-password?token="));
    }


    @Test
    void shouldPropagateExceptionWhenEmailFails() {
        // 3. Email failure
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));

        doThrow(new RuntimeException("Mail Server down")).when(emailService).sendEmail(anyString(), anyString(), anyString());

        assertDoesNotThrow( () -> service.forgotPassword("john@test.com"));
    }


    //=========================================================
    // resetPassword()
    //=========================================================

    @Test
    void shouldThrowInvalidTokenWhenTokenNotFound() {
        when(passwordResetTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> service.resetPassword("invalid-token", "newPassword123!"));
    }

    @Test
    void shouldThrowInvalidTokenWhenTokenAlreadyUsed() {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken("used-token");
        resetToken.setUsed(true);
        resetToken.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));

        when(passwordResetTokenRepository.findByToken(anyString())).thenReturn(Optional.of(resetToken));

        assertThrows(InvalidTokenException.class, () -> service.resetPassword("used-token", "newPassword123!"));
    }

    @Test
    void shouldThrowTokenExpiredWhenTokenIsExpired() {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken("expired-token");
        resetToken.setUsed(false);
        resetToken.setExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));

        when(passwordResetTokenRepository.findByToken(anyString())).thenReturn(Optional.of(resetToken));

        assertThrows(TokenExpiredException.class, () -> service.resetPassword("expired-token", "newPassword123!"));

    }


    @Test
    void shouldUpdatePasswordAndMarkTokenAsUsedWhenValid() {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken("valid-token-hash");
        resetToken.setUsed(false);
        resetToken.setUser(user);
        resetToken.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));


        when(passwordResetTokenRepository.findByToken(anyString())).thenReturn(Optional.of(resetToken));

        when(passwordEncoder.encode("NewPassword123!")).thenReturn("encodedNewPassword");

        service.resetPassword("valid-token", "NewPassword123!");

        assertEquals("encodedNewPassword", user.getPassword());
        verify(userRepository).save(user);

        assertTrue(resetToken.isUsed());
        verify(passwordResetTokenRepository).save(resetToken);
    }
}