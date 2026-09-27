package org.example.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.exceptions.InvalidCredentialsException;
import org.example.exceptions.InvalidTokenException;
import org.example.model.*;
import org.example.request.AuthRequestDTO;
import org.example.request.RefreshTokenRequestDTO;
import org.example.response.JwtResponseDTO;
import org.example.service.JwtService;
import org.example.service.RefreshTokenService;
import org.example.service.UserDetailsServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TokenController {

    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtService jwtService;



    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> authenticateAndGetToken(@Valid @RequestBody AuthRequestDTO authRequestDTO) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authRequestDTO.getUsername(), authRequestDTO.getPassword()));
        if (!authentication.isAuthenticated()) {
            throw new InvalidCredentialsException("Authentication failed");
        }

        RefreshTokenResponse refreshToken = refreshTokenService.createRefreshToken(authRequestDTO.getUsername());
        UserInfo user = userDetailsService.checkIfUserAlreadyExist(authRequestDTO.getUsername());
        return ResponseEntity.ok(JwtResponseDTO.builder()
                .accessToken(jwtService.generateToken(authRequestDTO.getUsername(), user.getUserId(), user.getRoles().stream().map(UserRole::name).toList()))
                .token(refreshToken.getToken())
                .build());
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<JwtResponseDTO> refreshToken(@Valid @RequestBody RefreshTokenRequestDTO req) {

         JwtResponseDTO response= refreshTokenService.rotateRefreshToken(req.getToken());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/change/passwords")
    public ResponseEntity<JwtResponseDTO> changePassword(@Valid @RequestBody PasswordDto passwordDto) {
        JwtResponseDTO response = userDetailsService.changePassword(passwordDto);

        return ResponseEntity.ok(response);

    }

    @PostMapping("/change/username")
    public ResponseEntity<JwtResponseDTO> changeUsername(Authentication authentication, @Valid @RequestBody UpdateUsernameReq usernameReq) {
        String userId=authentication.getName();

        JwtResponseDTO response = userDetailsService.changeUsername(usernameReq.getNewUsername(), userId);

        return ResponseEntity.ok(response);
    }


    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        userDetailsService.forgotPassword(request.email());
        return ResponseEntity.ok("Password reset email sent");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {

        userDetailsService.resetPassword(request.token(), request.newPassword());

        return ResponseEntity.ok("Password updated successfully");
    }




}
