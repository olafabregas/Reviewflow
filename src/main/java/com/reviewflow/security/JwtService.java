package com.reviewflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    @Getter
    private final long accessExpirationMs;
    @Getter
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms:900000}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(UserDetails userDetails) {
        return generateAccessToken(userDetails, null);
    }

    public String generateAccessToken(UserDetails userDetails, String userAgent) {
        if (userDetails instanceof ReviewFlowUserDetails details) {
            var builder = Jwts.builder()
                    .subject(userDetails.getUsername())
                    .claim("userId", details.getUserId())
                    .claim("role", details.getRole().name())
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + accessExpirationMs));
            
            if (userAgent != null) {
                builder.claim("userAgent", userAgent);
            }
            
            return builder.signWith(secretKey).compact();
        }
        var builder = Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpirationMs));
        
        if (userAgent != null) {
            builder.claim("userAgent", userAgent);
        }
        
        return builder.signWith(secretKey).compact();
    }

    public String generateRefreshToken() {
        return Jwts.builder()
                .subject("refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(secretKey)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public String extractClaim(String token, String claimKey) {
        return extractClaims(token).get(claimKey, String.class);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        if (token == null || token.isBlank()) return false;
        try {
            String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractClaims(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
