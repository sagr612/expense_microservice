package org.example.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.example.auth.JwtAuthFilter;
import org.example.controller.TokenController;
import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.exceptions.DuplicateResourceException;
import org.example.model.PasswordDto;
import org.example.model.RefreshTokenResponse;
import org.example.model.UpdateUsernameReq;
import org.example.request.AuthRequestDTO;
import org.example.request.RefreshTokenRequestDTO;
import org.example.response.JwtResponseDTO;
import org.example.service.JwtService;
import org.example.service.RefreshTokenService;
import org.example.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TokenController.class)
//@Import(org.example.auth.SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class TokenControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;
    @MockBean
    AuthenticationManager authenticationManager;

    @MockBean
    RefreshTokenService refreshTokenService;

    @MockBean
    UserDetailsServiceImpl userDetailsService;

    @MockBean
    JwtService jwtService;

    @MockBean
    JwtAuthFilter jwtAuthFilter;

    @MockBean
    Tracer tracer;

    //---------------------------------------------------------
    // login
    //---------------------------------------------------------

    @Test
    void shouldLoginSuccessfully() throws Exception {

        AuthRequestDTO request = AuthRequestDTO.builder()
                .username("john")
                .password("password")
                .build();

        when(authenticationManager.authenticate(any()))
                .thenReturn(new TestingAuthenticationToken(
                        "john",
                        null,
                        "ROLE_USER"
                ));

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .tokenHash("refresh-token")
                        .build();

        when(refreshTokenService.createRefreshToken("john"))
                .thenReturn(RefreshTokenResponse.builder().token("refresh-token").build());

        UserInfo user =
                UserInfo.builder()
                        .userId("user-1")
                        .username("john")
                        .roles(Set.of(UserRole.ROLE_USER))
                        .build();

        when(userDetailsService.checkIfUserAlreadyExist("john"))
                .thenReturn(user);

        when(jwtService.generateToken(anyString(), anyString(), anyList()))
                .thenReturn("jwt-token");

        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.token").value("refresh-token"));

        verify(authenticationManager).authenticate(any());
        verify(refreshTokenService).createRefreshToken("john");
    }

    @Test
    void shouldReturnUnauthorizedWhenAuthenticationFails() throws Exception {

        AuthRequestDTO request = AuthRequestDTO.builder()
                .username("john")
                .password("wrong")
                .build();

        when(authenticationManager.authenticate(any()))
                .thenReturn(new TestingAuthenticationToken(
                        "john",
                        null
                ));

        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

    }

    @Test
    void shouldLoginEvenWithEmptyFields() throws Exception {

        AuthRequestDTO request =
                new AuthRequestDTO();

        when(authenticationManager.authenticate(any()))
                .thenThrow(new RuntimeException("Authentication failed"));

        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

    }

    //---------------------------------------------------------
    // refresh token
    //---------------------------------------------------------

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {

        RefreshTokenRequestDTO dto =
                new RefreshTokenRequestDTO("refresh");

        UserInfo user =
                UserInfo.builder()
                        .userId("1")
                        .username("john")
                        .roles(Set.of(UserRole.ROLE_USER))
                        .build();

        RefreshToken token =
                RefreshToken.builder()
                        .tokenHash("refresh")
                        .userInfo(user)
                        .build();

        when(refreshTokenService.findByToken("refresh"))
                .thenReturn(Optional.of(token));
        when(refreshTokenService.createRefreshToken(anyString()))
                .thenReturn(RefreshTokenResponse.builder().token("refresh").build());
        when(refreshTokenService.verifyExpirationAndDeleteIfExpired(token))
                .thenReturn(token);

        when(jwtService.generateToken(anyString(), anyString(), anyList()))
                .thenReturn("new-access");

        mockMvc.perform(post("/auth/v1/refreshToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("new-access"))
                .andExpect(jsonPath("$.token")
                        .value("refresh"));

    }

    @Test
    void shouldReturnUnauthorizedWhenRefreshTokenMissing() throws Exception {

        RefreshTokenRequestDTO dto =
                new RefreshTokenRequestDTO("invalid");

        when(refreshTokenService.findByToken(anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/v1/refreshToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());

    }
    //---------------------------------------------------------
    // change password
    //---------------------------------------------------------

    @Test
    void shouldChangePasswordSuccessfully() throws Exception {

        PasswordDto dto = PasswordDto.builder()
                .oldPassword("oldPassword")
                .newPassword("newPassword")
                .build();

        JwtResponseDTO response =
                JwtResponseDTO.builder()
                        .accessToken("jwt-token")
                        .token("refresh-token")
                        .build();

        when(userDetailsService.changePassword(any()))
                .thenReturn(response);

        mockMvc.perform(post("/auth/v1/change/passwords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("jwt-token"))
                .andExpect(jsonPath("$.token")
                        .value("refresh-token"));

        verify(userDetailsService).changePassword(any());

    }

    @Test
    void shouldReturnNotFoundWhenChangingPasswordFails() throws Exception {

        PasswordDto dto = PasswordDto.builder()
                .oldPassword("wrong")
                .newPassword("newPassword")
                .build();

        when(userDetailsService.changePassword(any()))
                .thenThrow(new UsernameNotFoundException("Incorrect old password"));

        mockMvc.perform(post("/auth/v1/change/passwords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());

    }

    //---------------------------------------------------------
    // change username
    //---------------------------------------------------------

    @Test
    void shouldChangeUsernameSuccessfully() throws Exception {

        UpdateUsernameReq request =
                UpdateUsernameReq.builder()
                        .currentUsername("john")
                        .newUsername("john123")
                        .build();

        JwtResponseDTO response =
                JwtResponseDTO.builder()
                        .accessToken("jwt-token")
                        .token("refresh-token")
                        .build();

        when(userDetailsService.changeUsername(
                "john123",
                "john"))
                .thenReturn(response);

        mockMvc.perform(post("/auth/v1/change/username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("jwt-token"))
                .andExpect(jsonPath("$.token")
                        .value("refresh-token"));

        verify(userDetailsService)
                .changeUsername("john123", "john");

    }

    @Test
    void shouldReturnConflictWhenUsernameAlreadyExists() throws Exception {

        UpdateUsernameReq request =
                UpdateUsernameReq.builder()
                        .currentUsername("john")
                        .newUsername("john123")
                        .build();

        when(userDetailsService.changeUsername(anyString(), anyString()))
                .thenThrow(new DuplicateResourceException("Username already taken"));

        mockMvc.perform(post("/auth/v1/change/username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

    }


    //---------------------------------------------------------
    // validation
    //---------------------------------------------------------

    @Test
    void shouldRejectInvalidChangeUsernameRequest() throws Exception {

        UpdateUsernameReq request =
                UpdateUsernameReq.builder()
                        .currentUsername("")
                        .newUsername("")
                        .build();

        mockMvc.perform(post("/auth/v1/change/username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

    }

    @Test
    void shouldRejectInvalidRefreshTokenRequest() throws Exception {

        RefreshTokenRequestDTO dto =
                new RefreshTokenRequestDTO(null);

        mockMvc.perform(post("/auth/v1/refreshToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

    }


}