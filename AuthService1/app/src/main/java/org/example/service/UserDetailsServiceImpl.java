package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entities.OutboxEvent;
import org.example.entities.PasswordResetToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.eventProducer.UserInfoEvent;
import org.example.eventProducer.UserInfoProducer;
import org.example.exceptions.*;
import org.example.model.PasswordDto;
import org.example.model.RefreshTokenResponse;
import org.example.model.UserInfoDto;
import org.example.repository.OutboxRepository;
import org.example.repository.PasswordResetTokenRepository;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.example.response.JwtResponseDTO;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserInfo user = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user == null) {
            throw new UsernameNotFoundException("User not found...");
        }
        return new CustomUserDetails(user);
    }

    public UserInfo checkIfUserAlreadyExist(String username) {
        UserInfo userInfo = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return userInfo;
    }

    @Transactional
    public UserInfo signupUser(@Valid UserInfoDto dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new UserAlreadyExistsException("Username taken");

        }
        if (userRepository.existsByEmail(dto.getEmail())) throw new UserAlreadyExistsException("Email taken");
        if (userRepository.existsByPhoneNumber(dto.getPhoneNumber()))
            throw new UserAlreadyExistsException("Phone taken");

        String hashedPassword = passwordEncoder.encode(dto.getPassword());
        UserInfo userInfo = UserInfo.builder()
                .userId(UUID.randomUUID().toString())
                .username(dto.getUsername())
                .password(hashedPassword)
                .roles(Set.of(UserRole.ROLE_USER))
                .phoneNumber(dto.getPhoneNumber())
                .email(dto.getEmail())
                .build();


        UserInfo saved = userRepository.save(userInfo);
        try {
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateId(saved.getUserId())
                    .eventType("USER_CREATED")
                    .payload(objectMapper.writeValueAsString(userInfoEventToPublish(dto, userInfo.getUserId())))
                    .published(false)
                    .build();
            outboxRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to process json object while converting :", e);
            throw new IllegalStateException("Failed to process json object while converting :", e);
        }
        return saved;
    }

    private String getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof String s) return s;
        if (principal instanceof UserDetails ud) {
            // If UserDetails, we need to look up by username since userId isn't stored on it
            UserInfo user = userRepository.findByUsername(ud.getUsername()).orElseThrow(() -> new UsernameNotFoundException("User not found"));

            if (user == null) throw new ResourceNotFoundException("Authenticated user not found");
            return user.getUserId();
        }
        throw new UnauthorizedException("Cannot determine current user");
    }

    @Transactional
    public JwtResponseDTO changePassword(PasswordDto passwordDto) {

        String userId = getCurrentUserId();

        UserInfo user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!passwordEncoder.matches(passwordDto.getOldPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Incorrect old password");
        }

        if (passwordDto.getOldPassword().equals(passwordDto.getNewPassword())) {
            throw new IllegalArgumentException("New password cannot be same as old password");
        }

        user.setPassword(passwordEncoder.encode(passwordDto.getNewPassword()));
refreshTokenRepository.deleteByUserInfo(user);

        RefreshTokenResponse refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        return JwtResponseDTO.builder()
                .accessToken(jwtService.generateToken(user.getUsername(), user.getUserId(), user.getRoles().stream().map(UserRole::name).toList()))
                .token(refreshToken.getToken())
                .build();

    }

    private UserInfoEvent userInfoEventToPublish(UserInfoDto userInfoDto, String userId) {
        return UserInfoEvent.builder().userId(userId).firstName(userInfoDto.getFirstName()).lastName(userInfoDto.getLastName()).email(userInfoDto.getEmail()).phoneNumber(userInfoDto.getPhoneNumber()).build();
    }


    public String getUserByUsername(String name) {

        return userRepository.findByUsername(name)
                .map(UserInfo::getUserId)
                .orElse(null);
    }

    @Transactional
    public JwtResponseDTO changeUsername(String newUsername, String userId) {

        UserInfo user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (userRepository.existsByUsername(newUsername) && !newUsername.equals(user.getUsername())) {
            throw new DuplicateResourceException("Username already taken");
        }


        user.setUsername(newUsername);
        userRepository.save(user);

        RefreshTokenResponse refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        return JwtResponseDTO.builder()
                .accessToken(jwtService.generateToken(user.getUsername(), user.getUserId(), user.getRoles().stream().map(UserRole::name).toList()))
                .token(refreshToken.getToken())
                .build();
    }

    @Transactional
    public void forgotPassword(String email) {
        Optional<UserInfo> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return;
        }
        UserInfo user = userOptional.get();
        passwordResetTokenRepository.deleteByUserId(user.getUserId());
        String rawToken = generateSecureToken();
        String tokenHash = hash(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(tokenHash);
        resetToken.setUser(user);
        resetToken.setUsed(false);
        resetToken.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(resetToken);
        String resetLink = "expensetracker://reset-password?token=" + rawToken;


        try{
            emailService.sendEmail(
                    user.getEmail(),
                    "Reset Your Password",
                    "<h2>Reset Password</h2><a href='" + resetLink + "'>Reset</a> "
            );
        }catch (Exception e){
            log.warn("Unable to send email {}",e.getMessage());
        }

    }

    public String getUserByUserId(String userid) {
        UserInfo user = userRepository.findById(userid).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return user.getUserId();
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {

        String tokenHash = hash(token);

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByToken(tokenHash).orElseThrow(() -> new InvalidTokenException("Invalid reset token"));

        if (resetToken.isUsed()) {
            throw new InvalidTokenException("Reset token already used");
        }
        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenExpiredException("Reset token expired");
        }

        UserInfo user = resetToken.getUser();
        refreshTokenRepository.deleteByUserInfo(user);

        user.setPassword(passwordEncoder.encode(newPassword));

        userRepository.save(user);

        resetToken.setUsed(true);

        passwordResetTokenRepository.save(resetToken);
    }


    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
