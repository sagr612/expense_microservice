package com.greatest.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@Slf4j
public class FallbackController {


    private Mono<ResponseEntity<Map<String, Object>>> fallback(String service, String message) {
        log.warn("Circuit open — {} unavailable", service);
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "service", service,
                        "message", message,
                        "timestamp", Instant.now().toString()
                )));
    }

    private static final RequestMethod[] ALLOWED_METHODS = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH
    };

    @RequestMapping(value = "/fallback/expense", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH})
    public Mono<ResponseEntity<Map<String, Object>>> expenseFallback() {
        return fallback("expense-service", "Expense service is temporarily unavailable. Please try again later.");
    }
    @RequestMapping(
            value = "/fallback/auth",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH}
    )
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        return fallback("auth-service", "Auth service is temporarily unavailable. Please try again later.");
    }

    @RequestMapping(
            value = "/fallback/user",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH}
    )
    public Mono<ResponseEntity<Map<String, Object>>> userFallback() {
        return fallback("user-service", "User service is temporarily unavailable. Please try again later.");
    }

    @RequestMapping(
            value = "/fallback/notification",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH}
    )
    public Mono<ResponseEntity<Map<String, Object>>> notificationFallback() {
        return fallback("notification-service", "Notification service is temporarily unavailable. Please try again later.");
    }

    @RequestMapping(
            value = "/fallback/ai",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH}
    )

    public Mono<ResponseEntity<Map<String, Object>>> aiFallback() {
        return fallback("ai-service", "AI service is temporarily unavailable. Please try again later.");
    }
}