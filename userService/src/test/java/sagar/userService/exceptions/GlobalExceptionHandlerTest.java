package sagar.userService.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleCloudinary_returns502_withGenericMessage() {
        CloudinaryOperationException ex =
                new CloudinaryOperationException("upload failed", new IOException("network"));

        ResponseEntity<ErrorResponse> response = handler.handleCloudinary(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Image storage service unavailable; please retry");
        // must NOT leak internal cause
        assertThat(response.getBody().getMessage()).doesNotContain("network");
    }

    @Test
    void handleAuth_returns401() {
        AuthenticationException ex = new BadCredentialsException("bad token");
        ResponseEntity<ErrorResponse> response = handler.handleAuth(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handleAccessDenied_returns403() {
        AccessDeniedException ex = new AccessDeniedException("nope");
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleIllegalArgument_returns400_withMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("User ID cannot be blank");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("User ID cannot be blank");
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
        assertThat(response.getBody().getTimestamp()).isNotBlank();
    }

    @Test
    void handleUserBusy_returns409_withMessage() {
        UserBusyException ex = new UserBusyException("User is busy: user-1");

        ResponseEntity<ErrorResponse> response = handler.handleUserBusy(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("User is busy: user-1");
        assertThat(response.getBody().getStatus()).isEqualTo(409);
    }

    @Test
    void handleDistributedLock_returns409_withGenericSafeMessage() {
        DistributedLockException ex = new DistributedLockException("redis timeout on key user:lock:1");

        ResponseEntity<ErrorResponse> response = handler.handleDistributedLock(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // intentionally checking the response does NOT leak the raw exception message —
        // it should return the generic "please try again" text, not internal lock details
        assertThat(response.getBody().getMessage())
                .isEqualTo("Resource is currently being processed. Please try again.");
    }

    @Test
    void handleValidationErrors_returns400_withFieldLevelDetails() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("userInfoDto", "email", "Email is required");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("Validation Failed");
        assertThat(response.getBody().getValidationErrors())
                .containsEntry("email", "Email is required");
    }

    @Test
    void handleGenericException_returns500_andHidesInternalDetails() {
        Exception ex = new NullPointerException("some internal null reference we don't want exposed");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // must NOT leak ex.getMessage() to the client — that's the whole point of a generic handler
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getMessage()).doesNotContain("null reference");
    }
}
