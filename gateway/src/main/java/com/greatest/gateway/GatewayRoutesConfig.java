package com.greatest.gateway;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class GatewayRoutesConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final UserKeyResolver keyResolver;
    private final RedisRateLimiter authRateLimiter;
    private final RedisRateLimiter readRateLimiter;
    private final RedisRateLimiter writeRateLimiter;
    private final RedisRateLimiter aiRateLimiter;

    public GatewayRoutesConfig(JwtAuthFilter jwtAuthFilter, UserKeyResolver keyResolver, @Qualifier("authRateLimiter") RedisRateLimiter authRateLimiter, @Qualifier("readRateLimiter") RedisRateLimiter readRateLimiter, @Qualifier("writeRateLimiter") RedisRateLimiter writeRateLimiter, @Qualifier("aiRateLimiter") RedisRateLimiter aiRateLimiter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.keyResolver = keyResolver;
        this.authRateLimiter = authRateLimiter;
        this.readRateLimiter = readRateLimiter;
        this.writeRateLimiter = writeRateLimiter;
        this.aiRateLimiter = aiRateLimiter;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                .route("auth-service", r -> r.path("/auth/**").filters(f -> f.filter(jwtAuthFilter).requestRateLimiter(c -> {
                    c.setRateLimiter(authRateLimiter);
                    c.setKeyResolver(keyResolver);
                }).circuitBreaker(c -> c.setName("auth-service-cb").setFallbackUri("forward:/fallback/auth"))).uri("lb://AUTH-SERVICE"))

                .route("user-service-reads", r -> r.path("/api/v1/users/**").and().method(HttpMethod.GET).filters(f -> f.filter(jwtAuthFilter).requestRateLimiter(c -> {
                    c.setRateLimiter(readRateLimiter);
                    c.setKeyResolver(keyResolver);
                }).circuitBreaker(c -> c.setName("user-service-cb").setFallbackUri("forward:/fallback/user"))).uri("lb://USER-SERVICE"))

                .route("user-service-writes", r -> r.path("/api/v1/users/**").and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE).filters(f -> f.filter(jwtAuthFilter).requestRateLimiter(c -> {
                    c.setRateLimiter(writeRateLimiter);
                    c.setKeyResolver(keyResolver);
                }).circuitBreaker(c -> c.setName("user-service-cb").setFallbackUri("forward:/fallback/user"))).uri("lb://USER-SERVICE"))

                .route("expense-service-reads", r -> r.path("/api/v1/expenses/**").and().method(HttpMethod.GET).filters(f -> f.filter(jwtAuthFilter).requestRateLimiter(c -> {
                    c.setRateLimiter(readRateLimiter);
                    c.setKeyResolver(keyResolver);
                }).circuitBreaker(c -> c.setName("expense-service-cb").setFallbackUri("forward:/fallback/expense"))).uri("lb://EXPENSE-SERVICE"))

                .route("expense-service-writes", r -> r.path("/api/v1/expenses/**").and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE).filters(f -> f.filter(jwtAuthFilter).requestRateLimiter(c -> {
                    c.setRateLimiter(writeRateLimiter);
                    c.setKeyResolver(keyResolver);
                }).circuitBreaker(c -> c.setName("expense-service-cb").setFallbackUri("forward:/fallback/expense"))).uri("lb://EXPENSE-SERVICE"))

                .route("notification-service-reads", r -> r.path("/api/v1/notifications/**").and().method(HttpMethod.GET).filters(f -> f.filter(jwtAuthFilter).requestRateLimiter(c -> {
                    c.setRateLimiter(readRateLimiter);
                    c.setKeyResolver(keyResolver);
                }).circuitBreaker(c -> c.setName("notification-service-cb").setFallbackUri("forward:/fallback/notification"))).uri("lb://NOTIFICATION-SERVICE"))

                .route("notification-service", r -> r.path("/api/v1/notifications/**").and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE).filters(f -> f.filter(jwtAuthFilter).requestRateLimiter(c -> {
                    c.setRateLimiter(writeRateLimiter);
                    c.setKeyResolver(keyResolver);
                }).circuitBreaker(c -> c.setName("notification-service-cb").setFallbackUri("forward:/fallback/notification"))).uri("lb://NOTIFICATION-SERVICE"))

                .route("ai-service", r -> r.path("/api/v1/sms/**").filters(f -> f.filter(jwtAuthFilter).requestRateLimiter(c -> {
                    c.setRateLimiter(aiRateLimiter);
                    c.setKeyResolver(keyResolver);
                }).circuitBreaker(c -> c.setName("ai-service-cb").setFallbackUri("forward:/fallback/ai"))).uri("lb://AI-SERVICE"))

                .build();
    }


}
