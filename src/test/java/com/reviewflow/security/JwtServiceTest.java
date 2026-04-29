package com.reviewflow.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

  private JwtService jwtService;
  private final String secret = "test-secret-key-that-is-long-enough-for-sha256";
  private final SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

  @Mock private ReviewFlowUserDetails userDetails;

  @BeforeEach
  void setUp() {
    jwtService = new JwtService(secret, 900000, 604800000);
  }

  @Test
  void generateAccessToken_ShouldIncludeVersionClaim() {
    when(userDetails.getUsername()).thenReturn("user@test.com");
    when(userDetails.getUserId()).thenReturn(123L);
    when(userDetails.getRole()).thenReturn(com.reviewflow.shared.domain.UserRole.ADMIN);

    String token = jwtService.generateAccessToken(userDetails, 5);

    Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    assertEquals(5, claims.get("ver", Integer.class));
    assertEquals(123L, claims.get("userId", Long.class));
  }

  @Test
  void extractTokenVersion_ShouldReturnCorrectVersion() {
    String token = Jwts.builder()
        .claim("ver", 10)
        .signWith(secretKey)
        .compact();

    assertEquals(10, jwtService.extractTokenVersion(token));
  }
}
