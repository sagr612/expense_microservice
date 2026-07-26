package com.greatest.gateway;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class GatewayRoutesConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final UserKeyResolver keyResolver;
    private final RedisRateLimiter redisRateLimiter;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                .route("auth-service", r -> r
                        .path("/auth/**").filters(f -> f.filter(jwtAuthFilter).requestRateLimiter(c -> {
                            c.setRateLimiter(redisRateLimiter);
                            c.setKeyResolver(keyResolver);
                        }).circuitBreaker(c->c.setName("auth-service-cb")
                                .setFallbackUri("forward:/fallback/auth")))
                        .uri("lb://AUTH-SERVICE"))

                .route("user-service", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> f.filter(jwtAuthFilter)
                                .requestRateLimiter(c -> {
                                    c.setRateLimiter(redisRateLimiter);
                                    c.setKeyResolver(keyResolver);
                                }).circuitBreaker(c -> c
                                        .setName("user-service-cb")
                                        .setFallbackUri("forward:/fallback/user")
                                ))
                        .uri("lb://USER-SERVICE"))

                .route("expense-service", r -> r
                        .path("/api/v1/expenses/**")
                        .filters(f -> f.filter(jwtAuthFilter).requestRateLimiter(c -> {
                            c.setRateLimiter(redisRateLimiter);
                            c.setKeyResolver(keyResolver);
                        }).circuitBreaker(c -> c
                                .setName("expense-service-cb")
                                .setFallbackUri("forward:/fallback/expense")
                        ))
                        .uri("lb://EXPENSE-SERVICE"))

                .route("notification-service", r -> r
                        .path("/api/v1/notifications/**")
                        .filters(f -> f.filter(jwtAuthFilter)
                                .requestRateLimiter(c -> {
                                    c.setRateLimiter(redisRateLimiter);
                                    c.setKeyResolver(keyResolver);
                                }).circuitBreaker(c -> c
                                        .setName("notification-service-cb")
                                        .setFallbackUri("forward:/fallback/notification")
                                ))
                        .uri("lb://NOTIFICATION-SERVICE"))
                .route("ai-service", r -> r
                        .path("/api/v1/sms/**")
                        .filters(f -> f.filter(jwtAuthFilter)
                                .requestRateLimiter(c -> {
                                    c.setRateLimiter(redisRateLimiter);
                                    c.setKeyResolver(keyResolver);
                                }).circuitBreaker(c -> c
                                        .setName("ai-service-cb")
                                        .setFallbackUri("forward:/fallback/ai")
                                ))
                        .uri("lb://AI-SERVICE"))

                .build();
    }


}
