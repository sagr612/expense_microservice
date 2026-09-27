package org.example.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.auth.JwtAuthFilter;
import org.example.controller.TokenController;
import org.example.entities.RefreshToken;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.exceptions.DuplicateResourceException;
import org.example.exceptions.InvalidTokenException;
import org.example.model.*;
import org.example.request.AuthRequestDTO;
import org.example.request.RefreshTokenRequestDTO;
import org.example.response.JwtResponseDTO;
import org.example.service.JwtService;
import org.example.service.RefreshTokenService;
import org.example.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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

    private Authentication userAuth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    //---------------------------------------------------------
    // login
    //---------------------------------------------------------

    @Test
    void shouldLoginSuccessfully() throws Exception {

        AuthRequestDTO request = AuthRequestDTO.builder().username("john").password("password").build();

        when(authenticationManager.authenticate(any())).thenReturn(new TestingAuthenticationToken("john", null, "ROLE_USER"));

        RefreshToken refreshToken = RefreshToken.builder().tokenHash("refresh-token").build();

        when(refreshTokenService.createRefreshToken("john")).thenReturn(RefreshTokenResponse.builder().token("refresh-token").build());

        UserInfo user = UserInfo.builder().userId("user-1").username("john").roles(Set.of(UserRole.ROLE_USER)).build();

        when(userDetailsService.checkIfUserAlreadyExist("john")).thenReturn(user);

        when(jwtService.generateToken(anyString(), anyString(), anyList())).thenReturn("jwt-token");

        mockMvc.perform(post("/auth/v1/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("jwt-token")).andExpect(jsonPath("$.token").value("refresh-token"));

        verify(authenticationManager).authenticate(any());
        verify(refreshTokenService).createRefreshToken("john");
    }

    @Test
    void shouldReturnUnauthorizedWhenAuthenticationFails() throws Exception {

        AuthRequestDTO request = AuthRequestDTO.builder().username("john").password("wrong").build();

        when(authenticationManager.authenticate(any())).thenReturn(new TestingAuthenticationToken("john", null));

        mockMvc.perform(post("/auth/v1/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isUnauthorized());

    }

    @Test
    void shouldLoginEvenWithEmptyFields() throws Exception {

        AuthRequestDTO request = new AuthRequestDTO();

        when(authenticationManager.authenticate(any())).thenThrow(new RuntimeException("Authentication failed"));

        mockMvc.perform(post("/auth/v1/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isBadRequest());

    }

    //---------------------------------------------------------
    // refresh token
    //---------------------------------------------------------

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {

        RefreshTokenRequestDTO dto = new RefreshTokenRequestDTO("refresh");


        JwtResponseDTO mockResponse = JwtResponseDTO.builder().accessToken("new-access").token("refresh").build();

        when(refreshTokenService.rotateRefreshToken(anyString())).thenReturn(mockResponse);

        mockMvc.perform(post("/auth/v1/refreshToken").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("new-access")).andExpect(jsonPath("$.token").value("refresh"));

        verify(refreshTokenService).rotateRefreshToken("refresh");

    }

    @Test
    void shouldReturnUnauthorizedWhenRefreshTokenMissing() throws Exception {
        RefreshTokenRequestDTO dto = new RefreshTokenRequestDTO("invalid");

        when(refreshTokenService.rotateRefreshToken(anyString())).thenThrow(new InvalidTokenException("Refresh token not found"));

        mockMvc.perform(post("/auth/v1/refreshToken").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andExpect(status().isUnauthorized());

    }
    //---------------------------------------------------------
    // change password
    //---------------------------------------------------------

    @Test
    void shouldChangePasswordSuccessfully() throws Exception {

        PasswordDto dto = PasswordDto.builder().oldPassword("oldPassword1") // has letters + digits, length >= 8
                .newPassword("newPassword1") // has letters + digits, length >= 8
                .build();

        JwtResponseDTO response = JwtResponseDTO.builder().accessToken("jwt-token").token("refresh-token").build();

        when(userDetailsService.changePassword(any())).thenReturn(response);

        mockMvc.perform(post("/auth/v1/change/passwords")
//                        .with(authentication(userAuth(("user"))))
//                        .principal(userAuth("user"))

                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("jwt-token")).andExpect(jsonPath("$.token").value("refresh-token"));

        verify(userDetailsService).changePassword(any());

    }

    @Test
    void shouldReturnNotFoundWhenChangingPasswordFails() throws Exception {

        PasswordDto dto = PasswordDto.builder().oldPassword("oldPassword1") // has letters + digits, length >= 8
                .newPassword("newPassword1") // has letters + digits, length >= 8
                .build();

        when(userDetailsService.changePassword(any())).thenThrow(new UsernameNotFoundException("Incorrect old password"));

        mockMvc.perform(post("/auth/v1/change/passwords").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andExpect(status().isNotFound());

    }

    //---------------------------------------------------------
    // change username
    //---------------------------------------------------------

    @Test
    void shouldChangeUsernameSuccessfully() throws Exception {

        UpdateUsernameReq request = UpdateUsernameReq.builder().newUsername("john123").build();

        JwtResponseDTO response = JwtResponseDTO.builder().accessToken("jwt-token").token("refresh-token").build();

        when(userDetailsService.changeUsername("john123", "user")).thenReturn(response);

        mockMvc.perform(post("/auth/v1/change/username").principal(userAuth("user")).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("jwt-token")).andExpect(jsonPath("$.token").value("refresh-token"));

        verify(userDetailsService).changeUsername("john123", "user");

    }

    @Test
    void shouldReturnConflictWhenUsernameAlreadyExists() throws Exception {

        UpdateUsernameReq request = UpdateUsernameReq.builder().newUsername("john123").build();

        when(userDetailsService.changeUsername(anyString(), anyString())).thenThrow(new DuplicateResourceException("Username already taken"));

        mockMvc.perform(post("/auth/v1/change/username").principal(userAuth("user")).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isConflict());

    }


    //---------------------------------------------------------
    // validation
    //---------------------------------------------------------

    @Test
    void shouldRejectInvalidChangeUsernameRequest() throws Exception {

        UpdateUsernameReq request = UpdateUsernameReq.builder().newUsername("").build();

        mockMvc.perform(post("/auth/v1/change/username").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isBadRequest());

    }

    @Test
    void shouldRejectInvalidRefreshTokenRequest() throws Exception {

        RefreshTokenRequestDTO dto = new RefreshTokenRequestDTO(null);

        mockMvc.perform(post("/auth/v1/refreshToken").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andExpect(status().isBadRequest());

    }


    @Test
    void shouldAcceptForgotPasswordRequest() throws Exception {
        ForgotPasswordRequest forgotPasswordRequest = new ForgotPasswordRequest("john@test.com");

        mockMvc.perform(post("/auth/v1/forgot-password").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(forgotPasswordRequest))).andExpect(status().isOk()).andExpect(content().string("Password reset email sent"));
        verify(userDetailsService).forgotPassword("john@test.com");
    }

    @Test
    void shouldRejectForgotPasswordWithInvalidEmail() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("not-an-email");
        mockMvc.perform(post("/auth/v1/forgot-password").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isBadRequest());
    }

    @Test
    void shouldResetPasswordSuccessfully() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("token-123", "new-Password-123!");

        mockMvc.perform(post("/auth/v1/reset-password").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isOk()).andExpect(content().string("Password updated successfully"));

        verify(userDetailsService).resetPassword("token-123", "new-Password-123!");
    }
}