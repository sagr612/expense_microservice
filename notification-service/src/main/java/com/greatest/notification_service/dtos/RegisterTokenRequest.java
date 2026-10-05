package com.greatest.notification_service.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterTokenRequest {
    @NotBlank(message = "Expo token must not be blank")
    @Pattern(regexp = "^(ExponentPushToken|ExpoPushToken)\\[.+\\]$", message = "Invalid Expo push token format")
    private String expoToken;
}