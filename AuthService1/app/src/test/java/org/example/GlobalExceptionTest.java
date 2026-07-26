package org.example;

import org.example.exceptions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setup() {
        handler = new GlobalExceptionHandler();
    }

    //---------------------------------------------------------
    // handleUserAlreadyExists
    //---------------------------------------------------------

    @Test
    void shouldReturnConflictWhenUserAlreadyExists() {
        UserAlreadyExistsException ex = new UserAlreadyExistsException("User already exists with this email");

        ResponseEntity<ErrorResponse> response = handler.handleUserAlreadyExists(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("User already exists with this email", response.getBody().getMessage());
        assertEquals("Conflict", response.getBody().getError());
    }

    //---------------------------------------------------------
    // handleUsernameNotFound
    //---------------------------------------------------------

    @Test
    void shouldReturnNotFoundWhenUsernameNotFound() {
        UsernameNotFoundException ex = new UsernameNotFoundException("Username 'john' not found");

        ResponseEntity<ErrorResponse> response = handler.handleUsernameNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Username 'john' not found", response.getBody().getMessage());
        assertEquals("Not Found", response.getBody().getError());
    }

    //---------------------------------------------------------
    // handleUnauthorized
    //---------------------------------------------------------

    @Test
    void shouldReturnUnauthorizedWhenUnauthorizedExceptionThrown() {
        UnauthorizedException ex = new UnauthorizedException("Access denied");

        ResponseEntity<ErrorResponse> response = handler.handleUnauthorized(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Access denied", response.getBody().getMessage());
        assertEquals("Unauthorized", response.getBody().getError());
    }

    //---------------------------------------------------------
    // handleInvalidToken
    //---------------------------------------------------------

    @Test
    void shouldReturnUnauthorizedWhenTokenInvalid() {
        InvalidTokenException ex = new InvalidTokenException("Token has expired");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidToken(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Token has expired", response.getBody().getMessage());
        assertEquals("Unauthorized", response.getBody().getError());
    }

    //---------------------------------------------------------
    // handleValidationErrors (the loop-based one)
    //---------------------------------------------------------

    @Test
    void shouldReturnSingleFieldErrorWhenOneFieldInvalid() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError fieldError = new FieldError("userDto", "username", "Username is required");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation failed", response.getBody().getMessage());
        assertEquals(1, response.getBody().getValidationErrors().size());
        assertEquals("Username is required", response.getBody().getValidationErrors().get("username"));
    }

    @Test
    void shouldReturnAllFieldErrorsWhenMultipleFieldsInvalid() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError usernameError = new FieldError("userDto", "username", "Username is required");
        FieldError emailError = new FieldError("userDto", "email", "Email is invalid");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(usernameError, emailError));

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(2, response.getBody().getValidationErrors().size());
        assertEquals("Username is required", response.getBody().getValidationErrors().get("username"));
        assertEquals("Email is invalid", response.getBody().getValidationErrors().get("email"));
    }

    //---------------------------------------------------------
    // handleRuntimeException
    //---------------------------------------------------------

    @Test
    void shouldReturnBadRequestForGenericRuntimeException() {
        RuntimeException ex = new RuntimeException("Something went wrong");

        ResponseEntity<ErrorResponse> response = handler.handleRuntimeException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Something went wrong", response.getBody().getMessage());
        assertEquals("Bad Request", response.getBody().getError());
    }

    //---------------------------------------------------------
    // handleGenericException
    //---------------------------------------------------------

    @Test
    void shouldReturnInternalServerErrorForUnhandledException() {
        Exception ex = new Exception("Unexpected failure");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
        assertEquals("Internal Server Error", response.getBody().getError());
    }
}