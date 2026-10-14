package com.greatest.notification_service.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("You are not allowed to perform this action")
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation errors: {}", errors);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input data")
                .validationErrors(errors)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Authentication required or token invalid")
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }


    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal Argument: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder().timestamp(Instant.now().toString()).status(HttpStatus.BAD_REQUEST.value()).error("Bad Request").message(ex.getMessage()).build();
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotificationNotFoundException ex) {
        log.warn("Notification not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // 1. Missing or unparseable JSON body (The one that caused your 500 error!)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Missing or malformed request body: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Required request body is missing or malformed")
                .build());
    }

    // 2. Type mismatch in URL or Query param (e.g. page=abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = String.format("Parameter '%s' should be of type '%s'",
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(msg)
                .build());
    }

    // 3. Missing required query parameter
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(String.format("Required query parameter '%s' is missing", ex.getParameterName()))
                .build());
    }

    // 4. File upload exceeds 5MB limit
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .error("Payload Too Large")
                .message("Uploaded file exceeds the maximum allowed size (5MB)")
                .build());
    }

    // 5. Wrong HTTP method (e.g., POST instead of PUT)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error("Method Not Allowed")
                .message(String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod()))
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }


}
