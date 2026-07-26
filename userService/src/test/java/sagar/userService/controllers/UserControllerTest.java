package sagar.userService.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sagar.userService.controller.UserController;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.dtos.UserProfileImageResponse;
import sagar.userService.exceptions.DuplicateResourceException;
import sagar.userService.services.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private UserInfoDto validDto() {
        return UserInfoDto.builder()
                .userId("user-1")
                .firstName("Sagar")
                .lastName("Sharma")
                .phoneNumber(9999999999L)
                .email("sagar@example.com")
                .build();
    }

    @Test
    void createUpdate_returns201_whenPayloadIsValid() throws Exception {
        UserInfoDto dto = validDto();
        when(userService.createOrUpdateUser(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").value("user-1"));
    }

    @Test
    void createUpdate_returns400_whenRequiredFieldMissing() throws Exception {
        UserInfoDto invalid = validDto();
        invalid.setEmail(null);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.email").exists());

        verifyNoInteractions(userService);
    }

    @Test
    void createUpdate_returns409_whenEmailAlreadyExists() throws Exception {
        UserInfoDto dto = validDto();
        when(userService.createOrUpdateUser(any()))
                .thenThrow(new DuplicateResourceException("Email already in use: " + dto.getEmail()));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void getUser_returns200_whenUserExists() throws Exception {
        when(userService.getUserByUserId("user-1")).thenReturn(validDto());

        mockMvc.perform(get("/api/v1/users/user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value("user-1"));
    }

    @Test
    void getUser_returns400_whenUserNotFound() throws Exception {
        when(userService.getUserByUserId("missing"))
                .thenThrow(new IllegalArgumentException("User not found with userId: missing"));

        mockMvc.perform(get("/api/v1/users/missing"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteUser_returns204_onSuccess() throws Exception {
        doNothing().when(userService).deleteUser("user-1");

        mockMvc.perform(delete("/api/v1/users/user-1"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser("user-1");
    }

    @Test
    void uploadProfileImage_returns200_whenImageIsValid() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "avatar.png", "image/png", "fake-image-bytes".getBytes());

        when(userService.updateProfileImage(eq("user-1"), any()))
                .thenReturn(new UserProfileImageResponse("user-1", "https://cloudinary.com/avatar.png"));

        mockMvc.perform(multipart(HttpMethod.PATCH,"/api/v1/users/user-1/profile-image").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    void uploadProfileImage_returns400_whenFileIsEmpty() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "image", "avatar.png", "image/png", new byte[0]);

        mockMvc.perform(multipart(HttpMethod.PATCH,"/api/v1/users/user-1/profile-image").file(emptyFile))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void uploadProfileImage_returns400_whenContentTypeIsNotImage() throws Exception {
        MockMultipartFile nonImageFile = new MockMultipartFile(
                "image", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart(HttpMethod.PATCH,"/api/v1/users/user-1/profile-image").file(nonImageFile))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }
}
