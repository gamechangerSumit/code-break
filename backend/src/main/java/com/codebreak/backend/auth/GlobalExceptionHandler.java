package com.codebreak.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(
            RuntimeException exception
    ) {

        String message = exception.getMessage();

        if (message == null) {
            message = "An unexpected error occurred";
        }

        HttpStatus status;

        if (
                message.contains("not a member") ||
                        message.contains("permission")
        ) {
            status = HttpStatus.FORBIDDEN;
        } else if (
                message.contains("not found")
        ) {
            status = HttpStatus.NOT_FOUND;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }

        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "error",
                        message
                ));
    }
}