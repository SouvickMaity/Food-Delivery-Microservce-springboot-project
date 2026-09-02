package com.fooddelivery.auth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-days:15}")
    private long expirationDays;

    private SecretKey key() {
        // JS jsonwebtoken uses the raw string as HMAC key (UTF-8 bytes), not base64.
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(padTo256Bits(bytes));
    }

    private byte[] padTo256Bits(byte[] input) {
        if (input.length >= 32) return input;
        byte[] padded = new byte[32];
        System.arraycopy(input, 0, padded, 0, input.length);
        return padded;
    }

    public String generateToken(Map<String, Object> userClaim) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationDays * 24L * 60 * 60 * 1000);

        return Jwts.builder()
                .claim("user", userClaim)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
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
