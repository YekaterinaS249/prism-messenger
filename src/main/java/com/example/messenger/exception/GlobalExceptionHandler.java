package com.example.messenger.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Turns Spring's default Bean Validation failures (thrown by @Valid on @RequestBody DTOs,
 * before the controller method body ever runs) into the same {"error": "..."} JSON shape
 * the rest of the API already uses for business-logic errors. Without this, validation
 * failures (e.g. a 51-character username) fell through to Spring Boot's default error
 * response, which the frontend didn't know how to read, so the user saw no message at all.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = (fieldError != null && fieldError.getDefaultMessage() != null)
                ? fieldError.getDefaultMessage()
                : "Некорректные данные запроса";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    /**
     * Fallback for controller methods that call a service throwing IllegalArgumentException for
     * a business-rule violation (e.g. "email already in use") without wrapping the call in their
     * own try/catch — without this, such an exception fell through to Spring's default handler
     * as a raw 500, the same class of bug this file was originally created to fix.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
