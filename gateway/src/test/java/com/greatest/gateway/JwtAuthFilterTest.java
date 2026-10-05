package com.greatest.gateway;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthFilterTest {
    private JwtAuthFilter filter;
    private final String secret = "dGVzdC1zZWNyZXQtMzItYnl0ZS1sb25nLWZvci1oczI1NiE=";
    private SecretKey key;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        jwtUtil.initKey();

        filter = new JwtAuthFilter(jwtUtil, new ObjectMapper());
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    private String createToken(String username, String userId, List<String> roles, long ttl) {
        return Jwts.builder()
                .subject(username)
                .issuer("expense-tracker-auth")
                .claims(Map.of("userId", userId, "roles", roles))
                .expiration(new Date(System.currentTimeMillis() + ttl))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Public path (/auth/v1/login) passes without token")
    void publicPath_passesThroughWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/auth/v1/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get(), "Public paths must pass through the chain");
    }

    @Test
    @DisplayName("Protected path without token returns 401 Unauthorized")
    void protectedPath_missingToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/auth/v1/expenses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Admin route with ROLE_USER returns 403 Forbidden")
    void adminRoute_nonAdminUser_returns403() {
        String userToken = createToken("user", "u-1", List.of("ROLE_USER"), 3600000);
        MockServerHttpRequest req = MockServerHttpRequest.post("/auth/v1/admin/users/1/promote")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        GatewayFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());

    }

    @Test
    @DisplayName("Valid JWT strips spoofed headers and injects verified user headers downstream")
    void validToken_stripsSpoofedHeaders_andInjectsVerifiedHeaders() {
        String token = createToken("real_user", "real-user-123", List.of("ROLE_USER"), 3600000);
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/v1/expenses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "attacker-id")
                .header("X-Roles", "ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        GatewayFilterChain chain = ex -> {
            HttpHeaders headers = ex.getRequest().getHeaders();
            assertEquals("real-user-123", headers.getFirst("X-User-Id"));
            assertEquals("real_user", headers.getFirst("X-Username"));
            assertEquals("ROLE_USER", headers.getFirst("X-Roles"));

            assertEquals("real-user-123", ex.getAttribute("userId"));
            return Mono.empty();
        };
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

}
