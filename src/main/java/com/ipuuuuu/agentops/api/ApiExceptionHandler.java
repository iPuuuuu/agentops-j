package com.ipuuuuu.agentops.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
final class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Map<String, String>>> handleApiException(ApiException error) {
        return ResponseEntity.status(error.status()).body(error(error.code(), error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Map<String, String>>> handleUnreadableJson() {
        return ResponseEntity.badRequest().body(error("invalid_request", "JSON object is required"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Map<String, String>>> handleUnsupportedMediaType() {
        return ResponseEntity.status(415).body(error("unsupported_media_type", "Content-Type must be application/json"));
    }

    private static Map<String, Map<String, String>> error(String code, String message) {
        return Map.of("error", Map.of("code", code, "message", message == null ? "" : message));
    }
}
