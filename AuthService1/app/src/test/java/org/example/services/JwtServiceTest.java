package org.example.services;

import io.jsonwebtoken.JwtException;
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
    private static final String SECRET = "/+lOUCWb0YrBbkNfHT4DAXjZ8Ejp1C0NlTwB0Jlq47c=";
    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "SECRET", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L);
    }
    @Test
    void shouldGenerateToken() {
        String token = jwtService.GenerateToken("john", "123", List.of("ROLE_USER"));
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void shouldExtractUsername() {
        String token = jwtService.GenerateToken("john", "123", List.of("ROLE_USER"));
        String username = jwtService.extractUsername(token);
        assertEquals("john", username);
    }

    @Test
    void shouldExtractUserId() {
        String token = jwtService.GenerateToken("john", "user-123", List.of("ROLE_USER"));
        String userId = jwtService.extractUserId(token);
        assertEquals("user-123", userId);
    }

    @Test
    void shouldExtractExpirationDate() {
        String token = jwtService.GenerateToken("john", "123", List.of("ROLE_USER"));
        Date expiration = jwtService.extractExpiration(token);
        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void shouldValidateTokenSuccessfully() {
        UserDetails user = User.withUsername("john").password("password").roles("USER").build();
        String token = jwtService.GenerateToken("john", "123", List.of("ROLE_USER"));
        assertTrue(jwtService.validateToken(token, user));
    }

    @Test
    void shouldReturnFalseForDifferentUser() {
        UserDetails user = User.withUsername("mike").password("password").roles("USER").build();
        String token = jwtService.GenerateToken("john", "123", List.of("ROLE_USER"));
        assertFalse(jwtService.validateToken(token, user));
    }

    @Test
    void shouldThrowExceptionForMalformedToken() {
        assertThrows(JwtException.class, () -> jwtService.extractUsername("invalid.token"));
    }

    @Test
    void shouldContainRolesClaim() {
        String token = jwtService.GenerateToken("john", "123", List.of("ROLE_ADMIN", "ROLE_USER"));
        String username = jwtService.extractUsername(token);
        String userId = jwtService.extractUserId(token);
        assertEquals("john", username);
        assertEquals("123", userId);
    }
}