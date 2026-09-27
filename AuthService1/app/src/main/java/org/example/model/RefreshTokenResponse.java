package org.example.model;

import lombok.*;
import org.example.entities.RefreshToken;

import java.time.Instant;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RefreshTokenResponse {
    String token;
    Instant expiryDate;
}
