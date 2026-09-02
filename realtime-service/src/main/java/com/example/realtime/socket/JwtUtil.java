package com.example.realtime.socket;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Equivalent of the Node `jwt.verify(token, process.env.JWT_SEC)` call.
 *
 * Note: JJWT's Keys.hmacShaKeyFor() enforces a minimum 256-bit HS256 key and
 * would reject a short secret like "souvick123456789". We build the
 * SecretKeySpec directly instead, matching the permissive behavior of the
 * Node `jsonwebtoken` library. For production, use a longer, random secret.
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public Claims verifyAndDecode(String token) {
        SecretKey key = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
