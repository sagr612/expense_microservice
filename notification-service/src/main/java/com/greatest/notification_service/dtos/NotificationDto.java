package com.greatest.notification_service.dtos;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private String id;

    private String title;

    private String body;

    private String type;

    private Boolean isRead;

    private Instant createdAt;
}