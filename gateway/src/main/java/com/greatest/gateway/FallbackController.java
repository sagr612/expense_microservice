package com.greatest.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@Slf4j
public class FallbackController {

    @RequestMapping("/fallback/expense")
    public Mono<ResponseEntity<Map<String, Object>>> expenseFallback() {
        log.warn("Circuit open — expense-service unavailable");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", 503,
                        "service", "expense-service",
                        "message", "Expense service is temporarily unavailable. Please try again later.",
                        "timestamp", Instant.now().toString()
                )));
    }

    @RequestMapping("/fallback/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        log.warn("Circuit open — auth-service unavailable");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", 503,
                        "service", "auth-service",
                        "message", "Auth service is temporarily unavailable. Please try again later.",
                        "timestamp", Instant.now().toString()
                )));
    }

    @RequestMapping("/fallback/user")
    public Mono<ResponseEntity<Map<String, Object>>> userFallback() {
        log.warn("Circuit open — user-service unavailable");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", 503,
                        "service", "user-service",
                        "message", "User service is temporarily unavailable. Please try again later.",
                        "timestamp", Instant.now().toString()
                )));
    }

    @RequestMapping("/fallback/notification")
    public Mono<ResponseEntity<Map<String, Object>>> notificationFallback() {
        log.warn("Circuit open — notification-service unavailable");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", 503,
                        "service", "notification-service",
                        "message", "Notification service is temporarily unavailable.",
                        "timestamp", Instant.now().toString()
                )));
    }

    @RequestMapping("/fallback/ai")
    public Mono<ResponseEntity<Map<String, Object>>> aiFallback() {
        log.warn("Circuit open — ai-service unavailable");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", 503,
                        "service", "ai-service",
                        "message", "SMS classification is temporarily unavailable. Please log expenses manually.",
                        "timestamp", Instant.now().toString()
                )));
    }
}