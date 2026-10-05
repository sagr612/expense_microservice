package com.greatest.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JwtUtilTest {
    private JwtUtil jwtUtil;
    private final String secret = "dGVzdC1zZWNyZXQtMzItYnl0ZS1sb25nLWZvci1oczI1NiE="; // 256-bit base64 key
    private SecretKey key;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        jwtUtil.initKey();
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    @Test
    void shouldAcceptValidTokenWithRequiredClaims() {
        String token = Jwts.builder()
                .subject("john")
                .issuer("expense-tracker-auth")
                .claim("userId", "user-123")
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        Optional<Claims> claims = jwtUtil.parseAndValidate(token);

        assertTrue(claims.isPresent());
        assertEquals("john", claims.get().getSubject());
        assertEquals("user-123", claims.get().get("userId", String.class));
    }

    @Test
    void shouldRejectExpiredToken() {
        String token = Jwts.builder()
                .subject("john")
                .issuer("expense-tracker-auth")
                .claim("userId", "user-123")
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(key)
                .compact();

        Optional<Claims> claims = jwtUtil.parseAndValidate(token);

        assertTrue(claims.isEmpty());
    }

    @Test
    void shouldRejectTokenMissingUserId() {
        String token = Jwts.builder()
                .subject("john")
                .issuer("expense-tracker-auth")
//                .claims(Map.of("userId", "user-123"))
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        Optional<Claims> claims = jwtUtil.parseAndValidate(token);

        assertTrue(claims.isEmpty());
    }
    @Test
    void shouldRejectTokenMissingSubject() {
        String token = Jwts.builder()
                .issuer("expense-tracker-auth")
//                .subject("john")
                .claims(Map.of("userId", "user-123"))
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        Optional<Claims> claims = jwtUtil.parseAndValidate(token);

        assertTrue(claims.isEmpty());
    }
}
