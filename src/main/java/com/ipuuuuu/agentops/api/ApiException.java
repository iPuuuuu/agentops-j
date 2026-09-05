package com.ipuuuuu.agentops.api;

import org.springframework.http.HttpStatus;

final class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() { return status; }
    String code() { return code; }
}
