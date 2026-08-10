package org.example.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entities.OutboxEvent;
import org.example.entities.UserInfo;
import org.example.model.UserInfoDto;
import org.example.repository.OutboxRepository;
import org.example.repository.RefreshTokenRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanDb() {
        outboxRepository.deleteAll();
        // Refresh tokens have FK to users — delete them first
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }
    @Test
    void signup_persistsUserAndOutboxEventInSameTransaction() throws Exception {
        UserInfoDto dto = UserInfoDto.builder()
                .username("integration_user")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .email("integration@test.com")
                .phoneNumber("9876543210")
                .build();

        // Perform signup via HTTP layer
        mockMvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // Verify user was persisted
        UserInfo user = userRepository.findByUsername("integration_user");
        assertNotNull(user, "User should be in the DB");
        assertEquals("integration@test.com", user.getEmail());
        assertNotEquals("password123", user.getPassword(), "Password must be hashed");

        // Verify outbox event was written in the same transaction
        List<OutboxEvent> events =
                outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();
        assertEquals(1, events.size(), "One USER_CREATED event should be pending");
        assertEquals("USER_CREATED", events.get(0).getEventType());
        assertEquals(user.getUserId(), events.get(0).getAggregateId());
    }

    @Test
    void signup_returnsConflictOnDuplicateUsername() throws Exception {
        UserInfoDto dto = UserInfoDto.builder()
                .username("dup_user")
                .password("password123")
                .firstName("Test").lastName("User")
                .email("dup@test.com")
                .phoneNumber("1111111111")
                .build();

        // First signup — should succeed
        mockMvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Second identical signup — should fail with 409 Conflict
        UserInfoDto dup = UserInfoDto.builder()
                .username("dup_user")
                .password("password456")
                .firstName("Other").lastName("Person")
                .email("other@test.com")
                .phoneNumber("2222222222")
                .build();

        mockMvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict());
    }
}
