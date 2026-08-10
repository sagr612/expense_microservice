package org.example.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.auth.JwtAuthFilter;
import org.example.controller.AdminController;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.example.exceptions.DuplicateResourceException;
import org.example.exceptions.ResourceNotFoundException;
import org.example.repository.UserRepository;
import org.example.service.AdminService;
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


    @MockBean
    JwtService jwtService;                    // ← satisfies JwtAuthFilter dependency

    @MockBean
    AdminService adminService;
    @MockBean
    UserDetailsServiceImpl userDetailsService;

    @Test
    void shouldPromoteUserToAdmin() throws Exception {

        doNothing().when(adminService).promoteToAdmin("1");

        mockMvc.perform(post("/auth/v1/admin/users/1/promote"))
                .andExpect(status().isNoContent());

        verify(adminService).promoteToAdmin("1");
    }
    @Test
    void shouldReturnConflictWhenUserAlreadyAdmin() throws Exception {

        doThrow(new DuplicateResourceException("User is already an admin"))
                .when(adminService).promoteToAdmin("1");

        mockMvc.perform(post("/auth/v1/admin/users/1/promote"))
                .andExpect(status().isConflict());
    }


    @Test
    void shouldReturnNotFoundWhenPromotingMissingUser() throws Exception {

        doThrow(new ResourceNotFoundException("User not found: 1"))
                .when(adminService).promoteToAdmin("1");

        mockMvc.perform(post("/auth/v1/admin/users/1/promote"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRemoveAdminRole() throws Exception {

        doNothing().when(adminService).demoteAdmin("1");

        mockMvc.perform(delete("/auth/v1/admin/users/1/admin"))
                .andExpect(status().isNoContent());

        verify(adminService).demoteAdmin("1");
    }




    @Test
    void shouldReturnNotFoundWhenRemovingAdminFromMissingUser() throws Exception {

        doThrow(new ResourceNotFoundException("User not found: 1"))
                .when(adminService).demoteAdmin("1");

        mockMvc.perform(delete("/auth/v1/admin/users/1/admin"))
                .andExpect(status().isNotFound());
    }

}