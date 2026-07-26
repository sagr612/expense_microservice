package com.greatest.gateway;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.info.ConditionalOnEnabledInfoContributor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GatewayFilter {
    private final JwtUtil jwtUtil;
    private static final List<String> PUBLIC =
            List.of(
                    "/auth/v1/login",
                    "/auth/v1/signup",
                    "/auth/v1/refreshToken",
                    "/auth/v1/health"
            );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("JWT FILTER EXECUTED");
        String path = exchange.getRequest().getURI().getPath();
        if (PUBLIC.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {

            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);

            return exchange.getResponse().setComplete();
        }
        Claims claims = jwtUtil.extractClaims(token);

        String userId = claims.get("userId", String.class);
        List<String> roles = (List<String>) claims.get("roles");
        String username = claims.getSubject();
        if (path.startsWith("/auth/v1/admin")
                && !roles.contains("ROLE_ADMIN")) {

            exchange.getResponse()
                    .setStatusCode(HttpStatus.FORBIDDEN);

            return exchange.getResponse().setComplete();
        }
        ServerHttpRequest request =
                exchange.getRequest()
                        .mutate()
                        .header("X-User-Id", userId)
                        .header("X-Username", username)
                        .header("X-Roles", String.join(",", roles))
                        .build();
        log.info("UserId={}", userId);
        log.info("Username={}", username);
        return chain.filter(
                exchange.mutate()
                        .request(request)
                        .build());
    }
}
