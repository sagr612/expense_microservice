package com.greatest.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

public class UserKeyResolverTest {
    private UserKeyResolver keyResolver;

    @BeforeEach
    public void setup() {
        keyResolver = new UserKeyResolver();
    }

    @Test
    void shouldResolveVerifiedUserIdFromExchangeAttribute() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/expenses")
                .header("X-User-Id", "attacker-id")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getAttributes().put("userId", "verified-id");
        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("verified-id")
                .verifyComplete();
    }

    @Test
    void shouldFallbackToClientIpWhenUserIdAttributeMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/v1/login")
                .remoteAddress(new InetSocketAddress("192.168.1.50", 1234)).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("192.168.1.50")
                .verifyComplete();
    }


    @Test
    void shouldHandleNullRemoteAddressGracefullyWithoutNpe() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/v1/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("unknown-client").verifyComplete();
    }
}