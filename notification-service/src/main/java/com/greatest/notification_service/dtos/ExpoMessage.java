package com.greatest.notification_service.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpoMessage {
    private String to;
    private String title;
    private String body;
    private String sound;   // "default" plays the device default sound
}