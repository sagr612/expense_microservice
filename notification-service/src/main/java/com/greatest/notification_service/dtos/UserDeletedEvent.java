package com.greatest.notification_service.dtos;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeletedEvent {
    private String userId;
}