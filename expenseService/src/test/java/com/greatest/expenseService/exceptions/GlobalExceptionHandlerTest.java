package com.greatest.expenseService.exceptions;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleIllegalArgument_returns400_withMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Expense not found");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Expense not found");
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
    }

    @Test
    void handleValidationErrors_returns400_withFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("expenseDto", "amount", "Amount cannot be null");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getValidationErrors())
                .containsEntry("amount", "Amount cannot be null");
    }
    @Test
    void handleConstraintViolation_returns400_withParamName() {
        ConstraintViolationException ex = mock(ConstraintViolationException.class);
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        jakarta.validation.Path path = mock(jakarta.validation.Path.class);

        when(path.toString()).thenReturn("getExpenses.page");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be >= 0");
        when(ex.getConstraintViolations()).thenReturn(Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getValidationErrors()).containsKey("page");
    }

    @Test
    void handleOptimisticLockException_returns500_withGenericMessage() {
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("Expense", "id-1");

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(response.getBody().getMessage())
                .isEqualTo("Expense was modified by another transaction");
    }

    @Test
    void handleInvalidEnum_buildsFriendlyMessage_whenCauseIsInvalidFormatException() throws Exception {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        InvalidFormatException cause = mock(InvalidFormatException.class);

        JsonMappingException.Reference ref =
                new JsonMappingException.Reference(new Object(), "category");

        when(ex.getCause()).thenReturn(cause);
        when(cause.getTargetType()).thenReturn((Class) com.greatest.expenseService.entities.CategoryType.class);
        when(cause.getValue()).thenReturn("NOT_A_CATEGORY");
        when(cause.getPath()).thenReturn(List.of(ref));

        ResponseEntity<ErrorResponse> response = handler.handleInvalidEnum(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("NOT_A_CATEGORY");
        assertThat(response.getBody().getMessage()).contains("category");
    }

    @Test
    void handleInvalidEnum_fallsBackToGenericMessage_whenCauseIsNotInvalidFormatException() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getCause()).thenReturn(new RuntimeException("malformed json"));

        ResponseEntity<ErrorResponse> response = handler.handleInvalidEnum(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid request body");
    }

    @Test
    void handleGenericException_returns500_andHidesInternalDetails() {
        Exception ex = new NullPointerException("some internal detail we don't want leaked");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getMessage()).doesNotContain("internal detail");
    }
}