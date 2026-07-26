package org.example.exceptions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private Instant timestamp;
    private int statusCode;
    private String message;
    private String error;
    private Map<String, String> validationErrors;
}
