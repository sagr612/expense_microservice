package com.greatest.notification_service.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.notification_service.dtos.NotificationDto;
import com.greatest.notification_service.dtos.RegisterTokenRequest;
import com.greatest.notification_service.services.NotificationService;
import com.greatest.notification_service.services.PushTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PushTokenService pushTokenService;
    @MockitoBean private NotificationService notificationService;

    private Authentication userAuth(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    void registerToken_returns200_andSavesToken() throws Exception {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setExpoToken("ExponentPushToken[abc]");

        mockMvc.perform(post("/api/v1/notifications/register-token")
                        .with(authentication(userAuth("user-1")))
                        .with(csrf())                                      // ← ADDED
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(pushTokenService).saveToken("user-1", "ExponentPushToken[abc]");
    }

    @Test
    void getNotifications_returns200_withList() throws Exception {
        NotificationDto dto = NotificationDto.builder()
                .id("notif-1").title("Budget Alert").body("80% used")
                .type("BUDGET_ALERT").isRead(false).createdAt(Instant.now()).build();
        when(notificationService.getNotifications("user-1")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/notifications")
                        .with(authentication(userAuth("user-1"))))         // GET: no csrf needed
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("notif-1"))
                .andExpect(jsonPath("$[0].title").value("Budget Alert"));
    }

    @Test
    void getNotifications_returns200_withEmptyList_whenNoneExist() throws Exception {
        when(notificationService.getNotifications("user-2")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications")
                        .with(authentication(userAuth("user-2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void markRead_returns200_andDelegatesToService() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/notif-1/read")
                        .with(authentication(userAuth("user-1")))
                        .with(csrf()))                                     // ← ADDED
                .andExpect(status().isOk());

        verify(notificationService).markRead("notif-1", "user-1");
    }

    @Test
    void markRead_returns403_whenAccessDenied() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("Not your notification"))
                .when(notificationService).markRead("notif-2", "user-1");

        mockMvc.perform(patch("/api/v1/notifications/notif-2/read")
                        .with(authentication(userAuth("user-1")))
                        .with(csrf()))                                     // ← ADDED
                .andExpect(status().isForbidden());
    }

    @Test
    void unreadCount_returns200_withCount() throws Exception {
        when(notificationService.unreadCount("user-1")).thenReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(authentication(userAuth("user-1"))))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void unreadCount_returns200_withZero_whenNoUnread() throws Exception {
        when(notificationService.unreadCount("user-1")).thenReturn(0L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(authentication(userAuth("user-1"))))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }
}
