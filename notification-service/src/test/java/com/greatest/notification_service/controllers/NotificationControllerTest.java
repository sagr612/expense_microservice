package com.greatest.notification_service.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greatest.notification_service.dtos.NotificationDto;
import com.greatest.notification_service.dtos.RegisterTokenRequest;
import com.greatest.notification_service.exceptions.NotificationNotFoundException;
import com.greatest.notification_service.services.NotificationService;
import com.greatest.notification_service.services.PushTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PushTokenService pushTokenService;
    @MockitoBean
    private NotificationService notificationService;

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
        Page<NotificationDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);
        when(notificationService.getNotifications(eq("user-1"), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/notifications")
                        .with(authentication(userAuth("user-1"))))         // GET: no csrf needed
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("notif-1"))
                .andExpect(jsonPath("$.content[0].title").value("Budget Alert"));
    }

    @Test
    void getNotifications_returns200_withEmptyList_whenNoneExist() throws Exception {
        Page<NotificationDto> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 1);
        when(notificationService.getNotifications(eq("user-2"), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/notifications")
                        .with(authentication(userAuth("user-2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());
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


    @Test
    void registerToken_returns400_whenExpoTokenIsBlank() throws Exception {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setExpoToken("");
        mockMvc.perform(post("/api/v1/notifications/register-token")
                        .with(authentication(userAuth("user-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.expoToken").exists());


        verifyNoInteractions(pushTokenService);
    }

    @Test
    void registerToken_returns400_whenExpoTokenIsInvalidFormat() throws Exception {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setExpoToken("InvalidTokenFormat123");
        mockMvc.perform(post("/api/v1/notifications/register-token")
                        .with(authentication(userAuth("user-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.expoToken").value("Invalid Expo push token format"));

        verifyNoInteractions(pushTokenService);
    }


    @Test
    void unregisterToken_returns204_andDeletesToken() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/token")
                        .with(authentication(userAuth("user-1")))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        verify(pushTokenService).deleteToken("user-1");
    }

    @Test
    void markRead_returns404_whenNotFound() throws Exception {
        doThrow(new NotificationNotFoundException("Notification not found"))
                .when(notificationService).markRead( "notif", "user-1");


        mockMvc.perform(patch("/api/v1/notifications/notif/read")
                        .with(authentication(userAuth("user-1")))
                        .with(csrf())
                ).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("404"))
                .andExpect(jsonPath("$.error").value("Not Found"));

    }

}
