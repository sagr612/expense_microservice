package org.example.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("User already exists: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.CONFLICT.value())
                .message(ex.getMessage())
                .error("Conflict")
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(UsernameNotFoundException ex) {
        log.warn("Username not found: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.NOT_FOUND.value())
                .message(ex.getMessage()) // FIXED: was .error() twice
                .error("Not Found")
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCreds(InvalidCredentialsException ex) {
        log.warn("Invalid creds: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .message(ex.getMessage())
                .error("Unauthorized")
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorResponse);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex) {
        log.warn("Duplicate resource conflict: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.CONFLICT.value())
                .message(ex.getMessage())
                .error("Conflict")
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                // Deliberately vague — don't leak "user not found" vs "wrong password"
                .message("Invalid credentials")
                .error("Unauthorized")
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.NOT_FOUND.value())
                .message(ex.getMessage())
                .error("Not Found")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }


    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex) {
        log.warn("Unauthorized access attempt: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .message(ex.getMessage())
                .error("Unauthorized")
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorResponse);
    }
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex) {
        log.warn("Invalid token: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .message(ex.getMessage())
                .error("Unauthorized")
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleExpiredToken(TokenExpiredException ex) {
        log.warn("Token expired: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .message(ex.getMessage())
                .error("Token Expired")
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        log.warn("Validation failed for fields: {}", errors);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .message("Validation failed")
                .error("Bad Request")
                .validationErrors(errors)
                .build();
        return ResponseEntity.badRequest().body(errorResponse);
    }


    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument  in request");

        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .error("Bad Request")
                .build());
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON payload in request");
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .message("Malformed JSON request")
                .error("Bad Request")
                .build());
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("Database constraint violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.CONFLICT.value())
                .message("Resource already exists or constraint violated")
                .error("Conflict")
                .build());
    }


    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(org.springframework.dao.OptimisticLockingFailureException ex) {
        log.warn("Concurrent modification detected: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.CONFLICT.value())
                .message("Concurrent transaction conflict. Please retry.")
                .error("Conflict")
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Internal server error", ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("An unexpected error occurred")
                .error("Internal Server Error")
                .build();
        return ResponseEntity.internalServerError().body(errorResponse);
    }
}