package org.example.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.auth.JwtAuthFilter;
import org.example.auth.SecurityConfig;
import org.example.controller.AuthController;
import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.model.RefreshTokenResponse;
import org.example.model.UserInfoDto;
import org.example.response.JwtResponseDTO;
import org.example.service.JwtService;
import org.example.service.RefreshTokenService;
import org.example.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
//@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)

class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    JwtService jwtService;

    @MockBean
    RefreshTokenService refreshTokenService;

    @MockBean
    UserDetailsServiceImpl userDetailsService;

    @MockBean
    JwtAuthFilter jwtAuthFilter;

    //---------------------------------------------------------
    // signup
    //---------------------------------------------------------

    @Test
    void shouldSignupSuccessfully() throws Exception {

        UserInfoDto dto = UserInfoDto.builder()
                .username("john")
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .phoneNumber("9999999999")
                .build();

        UserInfo savedUser =
                UserInfo.builder()
                        .userId("user-1")
                        .username("john")
                        .roles(Set.of(UserRole.ROLE_USER))
                        .build();

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .tokenHash("refresh-token")
                        .build();

        when(userDetailsService.signupUser(any()))
                .thenReturn(savedUser);

        when(refreshTokenService.createRefreshToken("john"))
                .thenReturn(RefreshTokenResponse.builder().token("refresh-token").build());

        when(jwtService.generateToken(anyString(), anyString(), anyList()))
                .thenReturn("jwt-token");

        mockMvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.token").value("refresh-token"));

        verify(userDetailsService).signupUser(any());
        verify(refreshTokenService).createRefreshToken("john");
    }

    @Test
    void shouldReturnConflictWhenUsernameAlreadyExists() throws Exception {

        UserInfoDto dto = UserInfoDto.builder()
                .username("john")
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .phoneNumber("9999999999")
                .build();

        when(userDetailsService.signupUser(any()))
                .thenThrow(new org.example.exceptions.UserAlreadyExistsException(
                        "User already exists"));

        mockMvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());

    }

    @Test
    void shouldReturnBadRequestWhenValidationFails() throws Exception {

        UserInfoDto dto =
                UserInfoDto.builder()
                        .build();

        mockMvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

    }

    @Test
    void shouldGenerateJwtAfterSignup() throws Exception {

        UserInfoDto dto = UserInfoDto.builder()
                .username("john")
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .phoneNumber("9999999999")
                .build();

        UserInfo savedUser =
                UserInfo.builder()
                        .userId("user-1")
                        .username("john")
                        .roles(Set.of(UserRole.ROLE_USER))
                        .build();

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .tokenHash("refresh-token")
                        .build();

        when(userDetailsService.signupUser(any()))
                .thenReturn(savedUser);

        when(refreshTokenService.createRefreshToken(any()))
                .thenReturn(RefreshTokenResponse.builder().token("refresh-token").build());

        when(jwtService.generateToken(any(), any(), anyList()))
                .thenReturn("jwt-token");

        mockMvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        verify(jwtService).generateToken(any(), any(), anyList());

    }

    @Test
    void shouldCreateRefreshTokenAfterSignup() throws Exception {

        UserInfoDto dto = UserInfoDto.builder()
                .username("john")
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .phoneNumber("9999999999")
                .build();

        UserInfo savedUser =
                UserInfo.builder()
                        .userId("user-1")
                        .username("john")
                        .roles(Set.of(UserRole.ROLE_USER))
                        .build();

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .tokenHash("refresh-token")
                        .build();

        when(userDetailsService.signupUser(any()))
                .thenReturn(savedUser);

        when(refreshTokenService.createRefreshToken(any()))
                .thenReturn(RefreshTokenResponse.builder().token("refresh-token").build());

        when(jwtService.generateToken(any(), any(), anyList()))
                .thenReturn("jwt-token");

        mockMvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        verify(refreshTokenService)
                .createRefreshToken("john");

    }
    //---------------------------------------------------------
    // ping
    //---------------------------------------------------------

    @Test
    void shouldReturnUserIdWhenAuthenticated() throws Exception {

        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(
                        "john",
                        null
                );

        authentication.setAuthenticated(true);

        org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        when(userDetailsService.getUserByUserId("john"))
                .thenReturn("user-1");

        mockMvc.perform(get("/auth/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("user-1"));

        verify(userDetailsService)
                .getUserByUserId("john");

    }

    @Test
    void shouldReturnUnauthorizedWhenAuthenticationMissing() throws Exception {

        org.springframework.security.core.context.SecurityContextHolder
                .clearContext();

        mockMvc.perform(get("/auth/v1/ping"))
                .andExpect(status().isUnauthorized());

    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {

        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(
                        "john",
                        null
                );

        authentication.setAuthenticated(true);

        org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        when(userDetailsService.getUserByUserId("john"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        mockMvc.perform(get("/auth/v1/ping"))
                .andExpect(status().isNotFound());

    }

    //---------------------------------------------------------
    // health
    //---------------------------------------------------------

    @Test
    void shouldReturnHealthStatus() throws Exception {

        mockMvc.perform(get("/auth/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("UP"))
                .andExpect(jsonPath("$.timestamp")
                        .exists());

    }


}