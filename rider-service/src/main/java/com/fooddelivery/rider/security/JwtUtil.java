package com.fooddelivery.rider.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    private SecretKey key() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(padTo256Bits(bytes));
    }

    private byte[] padTo256Bits(byte[] input) {
        if (input.length >= 32) return input;
        byte[] padded = new byte[32];
        System.arraycopy(input, 0, padded, 0, input.length);
        return padded;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseUser(String token) {
        var claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return (Map<String, Object>) claims.get("user", Map.class);
    }
}
