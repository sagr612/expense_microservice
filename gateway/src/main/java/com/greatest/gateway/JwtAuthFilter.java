package com.greatest.gateway;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

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

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        byte[] body = ("{\"error\":\"" + status.getReasonPhrase() +
                "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.debug("JWT filter processing path {}", path);
        if (PUBLIC.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
           return writeError(exchange, HttpStatus.UNAUTHORIZED, "Missing or Invalid JWT Token");
        }
        String token = authHeader.substring(7);

        Optional<Claims> claimsOpt = jwtUtil.parseAndValidate(token);
        if (claimsOpt.isEmpty()) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "Missing or Invalid JWT Token");

        }
        Claims claims = claimsOpt.get();
        String userId = claims.get("userId", String.class);
        Object rolesObj = claims.get("roles");
        List<String> roles = (rolesObj instanceof List<?> list)
                ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();

        String username = claims.getSubject();
        if (path.startsWith("/auth/v1/admin")
                && !roles.contains("ROLE_ADMIN")) {

            return writeError(exchange, HttpStatus.FORBIDDEN, "Admin role required");

        }
        ServerHttpRequest request =
                exchange.getRequest()
                        .mutate()
                        .header("X-User-Id", userId)
                        .header("X-Username", username)
                        .header("X-Roles", String.join(",", roles))
                        .build();
        return chain.filter(
                exchange.mutate()
                        .request(request)
                        .build());
    }
}
