package com.fooddelivery.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * All routes in this service are mounted under /api/v1/**
 * and require authentication + admin role.
 *
 * OPTIONS requests are allowed through because they are
 * CORS preflight requests and do not contain the JWT.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // --------------------------------------------------
        // 1. Allow CORS preflight requests
        // --------------------------------------------------
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // --------------------------------------------------
        // 2. Ignore routes outside /api/v1/
        // --------------------------------------------------
        if (!path.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // --------------------------------------------------
        // 3. Get Authorization header
        // --------------------------------------------------
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(
                    response,
                    401,
                    "Please Login - No auth header"
            );
            return;
        }

        // --------------------------------------------------
        // 4. Extract JWT
        // --------------------------------------------------
        String token = authHeader.substring(7);

        if (token.isBlank()) {
            writeError(
                    response,
                    401,
                    "Please Login - Token missing"
            );
            return;
        }

        // --------------------------------------------------
        // 5. Validate JWT
        // --------------------------------------------------
        Map<String, Object> user;

        try {
            user = jwtUtil.parseUser(token);
        } catch (Exception e) {
            e.printStackTrace();

            writeError(
                    response,
                    401,
                    "Please Login - JWT error"
            );
            return;
        }

        // --------------------------------------------------
        // 6. Check user
        // --------------------------------------------------
        if (user == null) {
            writeError(
                    response,
                    401,
                    "Invalid token"
            );
            return;
        }

        // --------------------------------------------------
        // 7. Check admin role
        // --------------------------------------------------
        if (!"admin".equals(user.get("role"))) {
            writeError(
                    response,
                    403,
                    "Access denied"
            );
            return;
        }

        // --------------------------------------------------
        // 8. Store authenticated user
        // --------------------------------------------------
        request.setAttribute("authUser", user);

        // --------------------------------------------------
        // 9. Continue request
        // --------------------------------------------------
        filterChain.doFilter(request, response);
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");

        response.getWriter().write(
                objectMapper.writeValueAsString(
                        Map.of("message", message)
                )
        );
    }
}
