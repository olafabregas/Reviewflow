package com.reviewflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey secretKey;
  @Getter private final long accessExpirationMs;
  @Getter private final long refreshExpirationMs;

  public JwtService(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-expiration-ms:900000}") long accessExpirationMs,
      @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessExpirationMs = accessExpirationMs;
    this.refreshExpirationMs = refreshExpirationMs;
  }

  public String generateAccessToken(UserDetails userDetails) {
    return generateAccessToken(userDetails, null, 0);
  }

  public String generateAccessToken(UserDetails userDetails, int tokenVersion) {
    return generateAccessToken(userDetails, null, tokenVersion);
  }

  public String generateAccessToken(UserDetails userDetails, String userAgent) {
    return generateAccessToken(userDetails, userAgent, 0);
  }

  public String generateAccessToken(
      UserDetails userDetails, String userAgent, int tokenVersion) {
    Instant now = Instant.now();
    if (userDetails instanceof ReviewFlowUserDetails details) {
      var builder =
          Jwts.builder()
              .subject(userDetails.getUsername())
              .claim("userId", details.getUserId())
              .claim("role", details.getRole().name())
              .claim("ver", tokenVersion)
              .issuedAt(Date.from(now))
              .expiration(Date.from(now.plusMillis(accessExpirationMs)));

      if (userAgent != null) {
        builder.claim("userAgent", userAgent);
      }

      return builder.signWith(secretKey).compact();
    }
    var builder =
        Jwts.builder()
            .subject(userDetails.getUsername())
            .claim("ver", tokenVersion)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(accessExpirationMs)));

    if (userAgent != null) {
      builder.claim("userAgent", userAgent);
    }

    return builder.signWith(secretKey).compact();
  }

  public String generateRefreshToken() {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject("refresh")
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(refreshExpirationMs)))
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

  public Integer extractTokenVersion(String token) {
    return extractClaims(token).get("ver", Integer.class);
  }

  public Long extractUserId(String token) {
    return extractClaims(token).get("userId", Long.class);
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    // TODO [STYLE-AGENT]: fix structural violation
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
      return extractClaims(token).getExpiration().before(Date.from(Instant.now()));
    } catch (ExpiredJwtException e) {
      return true;
    }
  }

  private Claims extractClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }
}
