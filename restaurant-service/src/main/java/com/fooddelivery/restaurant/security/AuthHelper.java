package com.fooddelivery.restaurant.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuthHelper {

    private final JwtUtil jwtUtil;

    @Value("${app.internal-service-key}")
    private String internalServiceKey;

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

    /** Mirrors middlewares/isAuth.js. Throws AuthException with the matching status/message on failure. */
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
            throw new AuthException(500, "Please Login - JWT error");
        }
    }

    /** Mirrors middlewares/isAuth.js isSeller. */
    public void requireSeller(Map<String, Object> user) {
        if (user != null && !"seller".equals(user.get("role"))) {
            throw new AuthException(401, "You are not an authorized seller");
        }
    }

    /** Mirrors the internal-key check used on rider/payment-internal routes. */
    public void requireInternalKey(HttpServletRequest request) {
        String key = request.getHeader("x-internal-key");
        if (key == null || !key.equals(internalServiceKey)) {
            throw new AuthException(403, "Forbidden");
        }
    }
}
