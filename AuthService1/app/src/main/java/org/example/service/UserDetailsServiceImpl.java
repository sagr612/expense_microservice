package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entities.OutboxEvent;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.eventProducer.UserInfoEvent;
import org.example.eventProducer.UserInfoProducer;
import org.example.exceptions.*;
import org.example.model.PasswordDto;
import org.example.model.RefreshTokenResponse;
import org.example.model.UserInfoDto;
import org.example.repository.OutboxRepository;
import org.example.repository.UserRepository;
import org.example.response.JwtResponseDTO;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserInfo user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found...");
        }
        return new CustomUserDetails(user);
    }

    public UserInfo checkIfUserAlreadyExist(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public UserInfo signupUser(@Valid UserInfoDto dto) {
        if (Objects.nonNull(checkIfUserAlreadyExist(dto.getUsername())))
            throw new UserAlreadyExistsException("Username taken");
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
            UserInfo user = userRepository.findByUsername(ud.getUsername());
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

        return Optional.ofNullable(userRepository.findByUsername(name))
                .map(UserInfo::getUserId)
                .orElse(null);
    }

    public JwtResponseDTO changeUsername(String newUsername, String currentUsername) {
        if (userRepository.existsByUsername(newUsername)) {
            throw new DuplicateResourceException("Username already taken");
        }
        UserInfo user = userRepository.findByUsername(currentUsername);
        if (user == null) {
            throw new ResourceNotFoundException("User not found.");
        }

        user.setUsername(newUsername);
        userRepository.save(user);

        RefreshTokenResponse refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        return JwtResponseDTO.builder()
                .accessToken(jwtService.generateToken(user.getUsername(), user.getUserId(), user.getRoles().stream().map(UserRole::name).toList()))
                .token(refreshToken.getToken())
                .build();
    }
}
