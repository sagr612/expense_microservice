package org.example.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entities.UserInfo;
import org.example.exceptions.UnauthorizedException;
import org.example.model.RefreshTokenResponse;
import org.example.model.UserInfoDto;
import org.example.response.JwtResponseDTO;
import org.example.service.JwtService;
import org.example.service.RefreshTokenService;
import org.example.service.UserDetailsServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsServiceImpl userDetailsService;

    @PostMapping("/signup")
    public ResponseEntity<JwtResponseDTO> signUp(@Valid @RequestBody UserInfoDto userInfoDto) {
        log.info("Processing signup request for user: {}", userInfoDto.getUsername());
        UserInfo user = userDetailsService.signupUser(userInfoDto);
        RefreshTokenResponse refreshToken = refreshTokenService.createRefreshToken(userInfoDto.getUsername());
        String jwtToken = jwtService.generateToken(
                user.getUsername(), user.getUserId(),
                user.getRoles().stream().map(userRole -> userRole.name()).toList());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JwtResponseDTO.builder()
                        .accessToken(jwtToken)
                        .token(refreshToken.getToken())
                        .build());

    }


    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("User not authenticated");
        }

        String userId = userDetailsService.getUserByUserId(authentication.getName());


        return ResponseEntity.ok(userId);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", Instant.now()
        ));
    }


}
