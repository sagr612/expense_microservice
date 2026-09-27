package com.greatest.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GatewayFilter {
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    private static final List<String> PUBLIC = List.of(
            "/auth/v1/login", "/auth/v1/signup",
            "/auth/v1/refreshToken", "/auth/v1/health",
            "/auth/v1/forgot-password", "/auth/v1/reset-password"
    );

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> errorMap = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", exchange.getRequest().getURI().getPath()
        );

        try {
            byte[] body = objectMapper.writeValueAsBytes(errorMap);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception e) {
            byte[] fallback = "{\"error\":\"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(fallback)));

        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.debug("JWT filter processing path {}", path);

        if (PUBLIC.contains(path)) {
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
        List<String> roles = (rolesObj instanceof List<?> list) ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList() : List.of();

        String username = claims.getSubject();
        if (path.contains("/admin") && !roles.contains("ROLE_ADMIN")) {

            return writeError(exchange, HttpStatus.FORBIDDEN, "Admin role required");

        }

        exchange.getAttributes().put("userId", userId);
        ServerHttpRequest request = exchange.getRequest().mutate().headers(httpHeaders -> {
            httpHeaders.remove("X-User-Id");
            httpHeaders.remove("X-Username");
            httpHeaders.remove("X-Roles");
        }).header("X-User-Id", userId).header("X-Username", username).header("X-Roles", String.join(",", roles)).build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
