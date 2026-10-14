package com.greatest.expenseService.exceptions;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .build();

        return ResponseEntity.badRequest().body(error);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidEnum(HttpMessageNotReadableException ex) {
        log.warn("Invalid request body: {}", ex.getMessage());

        String message = "Invalid request body";

        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException invalidFormat) {
            if (invalidFormat.getTargetType().isEnum()) {
                String fieldName = invalidFormat.getPath().get(0).getFieldName();
                String invalidValue = invalidFormat.getValue().toString();
                String validValues = Arrays.stream(invalidFormat.getTargetType().getEnumConstants())
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                message = "Invalid value '%s' for field '%s'. Accepted values: [%s]"
                        .formatted(invalidValue, fieldName, validValues);
            }
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(message)
                .build();

        return ResponseEntity.badRequest().body(error);
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
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input data")
                .validationErrors(errors)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(Exception ex) {
        log.error("Optimistic Locking error", ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message("Expense was modified by another transaction")
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.error("Constraint Violation", ex);
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(error -> {
            String path = error.getPropertyPath().toString();
            String fieldName = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            errors.put(fieldName, error.getMessage());
        });
        log.warn("Constraint Violation: {}", errors);
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input data")
                .validationErrors(errors)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing query parameter: {}", ex.getParameterName());
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(String.format("Required query parameter '%s' is missing", ex.getParameterName()))
                .build());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter: {}", ex.getName());
        String msg = String.format("Parameter '%s' should be a valid number", ex.getName());
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(msg)
                .build());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        log.warn("Validation failed on request parameter: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Invalid query parameter value: month must be 1-12 and year >= 2000")
                .build());
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
