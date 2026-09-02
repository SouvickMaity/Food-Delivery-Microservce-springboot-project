package com.fooddelivery.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Applies to routes that require authentication. Mirrors auth/src/middlewares/isAuth.js.
 * On success, stores the decoded "user" map as a request attribute "authUser".
 * Controllers check for this attribute themselves (matches req.user pattern),
 * so this filter only rejects requests when the path requires auth and the token is missing/invalid.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    // Paths that require auth in this service (mirrors routes/auth.js isAuth usage)
    private static final Set<String> PROTECTED_EXACT = Set.of("/api/auth/add/role", "/api/auth/me");

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        if (!PROTECTED_EXACT.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, 401, "Please Login - No auth header");
            return;
        }

        String token = authHeader.substring(7);

        if (token.isBlank()) {
            writeError(response, 401, "Please Login - Token missing");
            return;
        }

        try {
            Map<String, Object> user = jwtUtil.parseUser(token);

            if (user == null) {
                writeError(response, 401, "Invalid token");
                return;
            }

            request.setAttribute("authUser", user);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            writeError(response, 500, "Please Login - JWT error");
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("message", message)));
    }
}
