package com.fooddelivery.rider.config;

import com.fooddelivery.rider.security.AuthHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthHelper.AuthException.class)
    public ResponseEntity<?> handleAuthException(AuthHelper.AuthException ex) {
        return ResponseEntity.status(ex.status).body(Map.of("message", ex.getMessage()));
    }
}
