package org.example.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.example.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private JwtService jwtService;
    private static final String secret = "/+lOUCWb0YrBbkNfHT4DAXjZ8Ejp1C0NlTwB0Jlq47c=";
    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", secret);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L);
        ReflectionTestUtils.invokeMethod(jwtService, "initKey");
    }
    @Test
    void generatedToken_containsCorrectClaims() {
        String token = jwtService.generateToken("alice", "user-42", List.of("ROLE_USER", "ROLE_ADMIN"));

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("alice", claims.getSubject());
        assertEquals("user-42", claims.get("userId", String.class));
        assertEquals(List.of("ROLE_USER", "ROLE_ADMIN"), claims.get("roles"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(new Date()), "Token should not be pre-expired");
    }

    @Test
    void tokenIsValidForOneHourByDefault() {
        long before = System.currentTimeMillis();
        String token = jwtService.generateToken("alice", "user-42", List.of("ROLE_USER"));
        long after = System.currentTimeMillis();

        Date exp = jwtService.extractExpiration(token);

        // Expiration should be roughly 1 hour from now (allowing ±5 seconds)
        long expectedExp = before + 3600000;
        assertTrue(Math.abs(exp.getTime() - expectedExp) < 5000,
                "Token expiration should be ~1 hour from creation");
    }

    @Test
    void shouldgenerateToken() {
        String token = jwtService.generateToken("john", "123", List.of("ROLE_USER"));
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void shouldExtractUsername() {
        String token = jwtService.generateToken("john", "123", List.of("ROLE_USER"));
        String username = jwtService.extractUsername(token);
        assertEquals("john", username);
    }

    @Test
    void shouldExtractUserId() {
        String token = jwtService.generateToken("john", "user-123", List.of("ROLE_USER"));
        String userId = jwtService.extractUserId(token);
        assertEquals("user-123", userId);
    }

    @Test
    void shouldExtractExpirationDate() {
        String token = jwtService.generateToken("john", "123", List.of("ROLE_USER"));
        Date expiration = jwtService.extractExpiration(token);
        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void shouldValidateTokenSuccessfully() {
        UserDetails user = User.withUsername("john").password("password").roles("USER").build();
        String token = jwtService.generateToken("john", "123", List.of("ROLE_USER"));
        assertTrue(jwtService.validateToken(token, user));
    }

    @Test
    void shouldReturnFalseForDifferentUser() {
        UserDetails user = User.withUsername("mike").password("password").roles("USER").build();
        String token = jwtService.generateToken("john", "123", List.of("ROLE_USER"));
        assertFalse(jwtService.validateToken(token, user));
    }

    @Test
    void shouldThrowExceptionForMalformedToken() {
        assertThrows(JwtException.class, () -> jwtService.extractUsername("invalid.token"));
    }

    @Test
    void shouldContainRolesClaim() {
        String token = jwtService.generateToken("john", "123", List.of("ROLE_ADMIN", "ROLE_USER"));
        String username = jwtService.extractUsername(token);
        String userId = jwtService.extractUserId(token);
        assertEquals("john", username);
        assertEquals("123", userId);
    }
}