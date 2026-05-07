package com.reviewflow.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import java.time.Instant;
import java.util.Date;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final JwtKeyRegistry jwtKeyRegistry;
  @Getter private final long accessExpirationMs;
  @Getter private final long refreshExpirationMs;

  public JwtService(
      JwtKeyRegistry jwtKeyRegistry,
      @Value("${jwt.access-expiration-ms:900000}") long accessExpirationMs,
      @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
    this.jwtKeyRegistry = jwtKeyRegistry;
    this.accessExpirationMs = accessExpirationMs;
    this.refreshExpirationMs = refreshExpirationMs;
  }

  public String generateAccessToken(UserDetails userDetails) {
    return generateAccessToken(userDetails, null, 0, null, null);
  }

  public String generateAccessToken(UserDetails userDetails, int tokenVersion) {
    return generateAccessToken(userDetails, null, tokenVersion, null, null);
  }

  public String generateAccessToken(UserDetails userDetails, String userAgent) {
    return generateAccessToken(userDetails, userAgent, 0, null, null);
  }

  public String generateAccessToken(UserDetails userDetails, String userAgent, int tokenVersion) {
    return generateAccessToken(userDetails, userAgent, tokenVersion, null, null);
  }

  public String generateAccessToken(
      UserDetails userDetails, String userAgent, int tokenVersion, Long stepUpAtEpochSeconds) {
    return generateAccessToken(userDetails, userAgent, tokenVersion, stepUpAtEpochSeconds, null);
  }

  /**
   * @param stepUpAtEpochSeconds optional epoch seconds when user last completed step-up challenge
   * @param accessTtlOverrideMs optional access TTL in ms (null = default from configuration)
   */
  public String generateAccessToken(
      UserDetails userDetails,
      String userAgent,
      int tokenVersion,
      Long stepUpAtEpochSeconds,
      Long accessTtlOverrideMs) {
    long ttlMs = accessTtlOverrideMs != null ? accessTtlOverrideMs : accessExpirationMs;
    Instant now = Instant.now();
    JwtKeyRegistry.SigningKey signing = jwtKeyRegistry.activeSigningKey();

    if (userDetails instanceof ReviewFlowUserDetails details) {
      var builder =
          Jwts.builder()
              .header()
              .keyId(signing.kid())
              .and()
              .subject(userDetails.getUsername())
              .claim("userId", details.getUserId())
              .claim("role", details.getRole().name())
              .claim("ver", tokenVersion)
              .issuedAt(Date.from(now))
              .expiration(Date.from(now.plusMillis(ttlMs)));

      if (userAgent != null) {
        builder.claim("userAgent", userAgent);
      }
      if (stepUpAtEpochSeconds != null) {
        builder.claim("stepUpAt", stepUpAtEpochSeconds);
      }

      return builder.signWith(signing.secretKey(), Jwts.SIG.HS256).compact();
    }

    var builder =
        Jwts.builder()
            .header()
            .keyId(signing.kid())
            .and()
            .subject(userDetails.getUsername())
            .claim("ver", tokenVersion)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(ttlMs)));

    if (userAgent != null) {
      builder.claim("userAgent", userAgent);
    }
    if (stepUpAtEpochSeconds != null) {
      builder.claim("stepUpAt", stepUpAtEpochSeconds);
    }

    return builder.signWith(signing.secretKey(), Jwts.SIG.HS256).compact();
  }

  public String generateRefreshToken() {
    return generateRefreshToken(refreshExpirationMs);
  }

  public String generateRefreshToken(long ttlMs) {
    Instant now = Instant.now();
    JwtKeyRegistry.SigningKey signing = jwtKeyRegistry.activeSigningKey();
    return Jwts.builder()
        .header()
        .keyId(signing.kid())
        .and()
        .subject("refresh")
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(ttlMs)))
        .signWith(signing.secretKey(), Jwts.SIG.HS256)
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

  /** Epoch seconds of last successful step-up, or null if claim absent. */
  public Long extractStepUpAt(String token) {
    try {
      Claims c = extractClaims(token);
      Object v = c.get("stepUpAt");
      if (v == null) {
        return null;
      }
      if (v instanceof Number n) {
        return n.longValue();
      }
      return null;
    } catch (Exception e) {
      return null;
    }
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
      return extractClaims(token).getExpiration().before(Date.from(Instant.now()));
    } catch (ExpiredJwtException e) {
      return true;
    }
  }

  private Claims extractClaims(String token) {
    try {
      return Jwts.parser()
          .keyLocator(header -> jwtKeyRegistry.verificationKeyForHeader(header))
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (SignatureException e) {
      throw e;
    }
  }
}
