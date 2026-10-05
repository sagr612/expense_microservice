package com.greatest.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;
    private SecretKey signKey;     // ← field to hold the cached key

    @PostConstruct
    void initKey() {                // ← void method, initializes the field
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.signKey = Keys.hmacShaKeyFor(keyBytes);
    }


    public Optional<Claims> parseAndValidate(String token) {
        try {
            Claims claims = extractClaims(token);
            if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
                return Optional.empty();
            }

            if (claims.getSubject() == null || claims.getSubject().isBlank()) {
                return Optional.empty();
            }

            String userId = claims.get("userId", String.class);
            if (userId == null || userId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }


    public Claims extractClaims(String token) {

        return Jwts.parser().verifyWith(signKey)
                .requireIssuer("expense-tracker-auth")
                .build().parseSignedClaims(token).getPayload();
    }
}