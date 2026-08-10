package org.example.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.example.entities.RefreshToken;

import java.time.Instant;

@Getter
@Setter
@Builder
public class RefreshTokenResponse {
    String token;
    Instant expiryDate;
}
