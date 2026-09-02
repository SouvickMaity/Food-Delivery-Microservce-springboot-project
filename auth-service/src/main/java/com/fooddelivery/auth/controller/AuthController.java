package com.fooddelivery.auth.controller;

import com.fooddelivery.auth.model.User;
import com.fooddelivery.auth.repository.UserRepository;
import com.fooddelivery.auth.security.JwtUtil;
import com.fooddelivery.auth.service.GoogleAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final List<String> ALLOWED_ROLES = List.of("customer", "rider", "seller");

    private final UserRepository userRepository;
    private final GoogleAuthService googleAuthService;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, GoogleAuthService googleAuthService, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.googleAuthService = googleAuthService;
        this.jwtUtil = jwtUtil;
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody Map<String, Object> body) {
        try {
            String code = (String) body.get("code");

            if (code == null || code.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Authorization code is required"));
            }

            Map<String, Object> profile = googleAuthService.exchangeCodeForProfile(code);

            String email = (String) profile.get("email");
            String name = (String) profile.get("name");
            String picture = (String) profile.get("picture");

            Optional<User> existing = userRepository.findByEmail(email);
            User user = existing.orElseGet(() -> {
                User u = new User();
                u.setName(name);
                u.setEmail(email);
                u.setImage(picture);
                u.setCreatedAt(Instant.now());
                u.setUpdatedAt(Instant.now());
                return userRepository.save(u);
            });

            Map<String, Object> userClaim = toClaimMap(user);
            String token = jwtUtil.generateToken(userClaim);

            return ResponseEntity.ok(Map.of(
                    "message", "Logged Success",
                    "token", token,
                    "user", userClaim
            ));
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // PUT /api/auth/add/role  (requires isAuth)
    @PutMapping("/add/role")
    public ResponseEntity<?> addUserRole(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> authUser = (Map<String, Object>) request.getAttribute("authUser");

            if (authUser == null || authUser.get("_id") == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
            }

            String role = (String) body.get("role");

            if (!ALLOWED_ROLES.contains(role)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid role"));
            }

            String userId = String.valueOf(authUser.get("_id"));
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            user.setRole(role);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            Map<String, Object> userClaim = toClaimMap(user);
            String token = jwtUtil.generateToken(userClaim);

            return ResponseEntity.ok(Map.of("user", userClaim, "token", token));
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    // GET /api/auth/me  (requires isAuth)
    @GetMapping("/me")
    public ResponseEntity<?> myProfile(HttpServletRequest request) {
        try {
            Object authUser = request.getAttribute("authUser");
            return ResponseEntity.ok(authUser);
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "Internal Server Error"));
        }
    }

    private Map<String, Object> toClaimMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_id", user.getId());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("image", user.getImage());
        map.put("role", user.getRole());
        return map;
    }
}
