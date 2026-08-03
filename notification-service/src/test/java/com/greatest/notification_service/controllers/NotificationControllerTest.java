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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PushTokenService pushTokenService;

    @MockitoBean
    private NotificationService notificationService;

    //---------------------------------------------------------
    // register-token
    //---------------------------------------------------------

    @Test
    void registerToken_returns200_andSavesToken() throws Exception {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setExpoToken("ExponentPushToken[abc]");

        mockMvc.perform(post("/api/v1/notifications/register-token")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(pushTokenService).saveToken("user-1", "ExponentPushToken[abc]");
    }

    //---------------------------------------------------------
    // get notifications
    //---------------------------------------------------------

    @Test
    void getNotifications_returns200_withList() throws Exception {
        NotificationDto dto = NotificationDto.builder()
                .id("notif-1")
                .title("Budget Alert")
                .body("80% used")
                .type("BUDGET_ALERT")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(notificationService.getNotifications("user-1")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("notif-1"))
                .andExpect(jsonPath("$[0].title").value("Budget Alert"));
    }

    @Test
    void getNotifications_returns200_withEmptyList_whenNoneExist() throws Exception {
        when(notificationService.getNotifications("user-2")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", "user-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    //---------------------------------------------------------
    // mark read
    //---------------------------------------------------------

    @Test
    void markRead_returns200_andDelegatesToService() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/notif-1/read"))
                .andExpect(status().isOk());

        verify(notificationService).markRead("notif-1");
    }

    //---------------------------------------------------------
    // unread count
    //---------------------------------------------------------

    @Test
    void unreadCount_returns200_withCount() throws Exception {
        when(notificationService.unreadCount("user-1")).thenReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void unreadCount_returns200_withZero_whenNoUnread() throws Exception {
        when(notificationService.unreadCount("user-1")).thenReturn(0L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }
}