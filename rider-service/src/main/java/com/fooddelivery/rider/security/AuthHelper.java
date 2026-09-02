package com.fooddelivery.rider.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuthHelper {

    private final JwtUtil jwtUtil;

    public AuthHelper(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public static class AuthException extends RuntimeException {
        public final int status;
        public AuthException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    public Map<String, Object> requireAuth(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AuthException(401, "Please Login - No auth header");
        }

        String token = authHeader.substring(7);

        if (token.isBlank()) {
            throw new AuthException(401, "Please Login - Token missing");
        }

        try {
            Map<String, Object> user = jwtUtil.parseUser(token);
            if (user == null) {
                throw new AuthException(401, "Invalid token");
            }
            return user;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Please Login - Jwt error");
        }
    }
}
