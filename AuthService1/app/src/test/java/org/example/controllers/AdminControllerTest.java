package org.example.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.auth.JwtAuthFilter;
import org.example.controller.AdminController;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.repository.UserRepository;
import org.example.service.JwtService;
import org.example.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    MockMvc mockMvc;

//    @Autowired
//    ObjectMapper objectMapper;

    @MockBean
    UserRepository userRepository;

//    @MockBean
//    JwtAuthFilter jwtAuthFilter;

//    @MockBean
//    PasswordEncoder passwordEncoder;
@MockBean
JwtService jwtService;                    // ← satisfies JwtAuthFilter dependency

    @MockBean
    UserDetailsServiceImpl userDetailsService;
    //---------------------------------------------------------
    // promote user
    //---------------------------------------------------------

    @Test
    void shouldPromoteUserToAdmin() throws Exception {

        UserInfo user = UserInfo.builder()
                .userId("1")
                .username("john")
                .roles(new HashSet<>(Set.of(UserRole.ROLE_USER)))
                .build();

        when(userRepository.findById("1"))
                .thenReturn(Optional.of(user));

        mockMvc.perform(post("/auth/v1/admin/users/1/promote")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string("User promoted to admin successfully"));

        verify(userRepository).save(any(UserInfo.class));
    }

    @Test
    void shouldReturnAlreadyAdminMessage() throws Exception {

        UserInfo user = UserInfo.builder()
                .userId("1")
                .username("john")
                .roles(Set.of(UserRole.ROLE_ADMIN))
                .build();

        when(userRepository.findById("1"))
                .thenReturn(Optional.of(user));

        mockMvc.perform(post("/auth/v1/admin/users/1/promote"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string("User is already an admin"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldReturnNotFoundWhenPromotingMissingUser() throws Exception {

        when(userRepository.findById("1"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/v1/admin/users/1/promote"))
                .andExpect(status().isNotFound());

    }
    //---------------------------------------------------------
    // remove admin
    //---------------------------------------------------------

    @Test
    void shouldRemoveAdminRole() throws Exception {

        UserInfo user = UserInfo.builder()
                .userId("1")
                .username("john")
                .roles(new HashSet<>(Set.of(UserRole.ROLE_ADMIN)))
                .build();

        when(userRepository.findById("1"))
                .thenReturn(Optional.of(user));

        mockMvc.perform(delete("/auth/v1/admin/users/1/admin"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string("Admin role removed successfully"));

        verify(userRepository).save(any(UserInfo.class));
    }

    @Test
    void shouldRemoveAdminRoleEvenWhenUserIsNotAdmin() throws Exception {

        UserInfo user = UserInfo.builder()
                .userId("1")
                .username("john")
                .roles(new HashSet<>(Set.of(UserRole.ROLE_USER)))
                .build();

        when(userRepository.findById("1"))
                .thenReturn(Optional.of(user));

        mockMvc.perform(delete("/auth/v1/admin/users/1/admin"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string("Admin role removed successfully"));

        verify(userRepository).save(any(UserInfo.class));
    }

    @Test
    void shouldReturnNotFoundWhenRemovingAdminFromMissingUser() throws Exception {

        when(userRepository.findById("1"))
                .thenReturn(Optional.empty());

        mockMvc.perform(delete("/auth/v1/admin/users/1/admin"))
                .andExpect(status().isNotFound());

    }

}