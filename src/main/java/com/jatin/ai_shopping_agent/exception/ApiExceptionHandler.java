package com.jatin.ai_shopping_agent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Validation failed", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedJson(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "Malformed request body", Map.of());
    }

    @ExceptionHandler(AiProviderUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleAiUnavailable(AiProviderUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InvalidCartQuantityException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidQuantity(InvalidCartQuantityException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(EmptyCartException.class)
    public ResponseEntity<Map<String, Object>> handleEmptyCart(EmptyCartException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        if (!fields.isEmpty()) {
            body.put("errors", fields);
        }
        return ResponseEntity.status(status).body(body);
    }
}
