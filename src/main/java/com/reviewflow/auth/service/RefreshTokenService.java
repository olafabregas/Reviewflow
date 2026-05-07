package com.reviewflow.auth.service;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.auth.exception.SessionExpiredException;
import com.reviewflow.auth.repository.RefreshTokenRepository;
import com.reviewflow.shared.domain.RefreshToken;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.exception.InactiveUserException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final AuditService auditService;

  public static String hashRefreshToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  @Transactional
  public RefreshToken createLoginSession(
      User user,
      String refreshPlain,
      Instant now,
      Instant expiresAt,
      String ip,
      String userAgent,
      String deviceId) {
    String familyId = UUID.randomUUID().toString();
    RefreshToken refreshToken =
        RefreshToken.builder()
            .user(user)
            .tokenHash(hashRefreshToken(refreshPlain))
            .expiresAt(expiresAt)
            .revoked(false)
            .createdAt(now)
            .sessionIssuedAt(now)
            .familyId(familyId)
            .parentTokenHash(null)
            .deviceId(deviceId)
            .ipCreated(truncateIp(ip))
            .userAgentCreated(truncateUserAgent(userAgent))
            .ipLastSeen(truncateIp(ip))
            .userAgentLastSeen(truncateUserAgent(userAgent))
            .sessionGroupId(null)
            .build();
    refreshToken = refreshTokenRepository.save(refreshToken);
    refreshToken.setSessionGroupId(refreshToken.getId());
    return refreshTokenRepository.save(refreshToken);
  }

  /**
   * Validates and rotates refresh token. Caller applies rate limits and issues access JWT.
   *
   * @param newRefreshPlain opaque refresh secret from {@code JwtService#generateRefreshToken()}
   */
  @Transactional
  public RotationResult rotate(
      String refreshTokenPlain,
      String newRefreshPlain,
      String clientIp,
      String userAgent,
      long refreshTtlMs,
      int absoluteCeilingHours,
      int idleTimeoutHours) {
    if (refreshTokenPlain == null || refreshTokenPlain.isBlank()) {
      throw new BadCredentialsException("Invalid refresh token");
    }

    String hash = hashRefreshToken(refreshTokenPlain);
    RefreshToken token =
        refreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

    User user = token.getUser();

    if (Boolean.TRUE.equals(token.getRevoked())) {
      String familyId = token.getFamilyId();
      refreshTokenRepository.revokeActiveTokensInFamily(familyId);
      auditService.log(
          user.getId(),
          "TOKEN_REUSE_ATTACK",
          "User",
          user.getId(),
          Map.of("family_id", familyId, "detail", "Refresh token reuse in family"),
          null);
      throw new BadCredentialsException("Invalid refresh token");
    }

    if (token.getExpiresAt().isBefore(Instant.now())) {
      throw new BadCredentialsException("Invalid refresh token");
    }

    if (token.getSessionIssuedAt() != null
        && Duration.between(token.getSessionIssuedAt(), Instant.now()).toHours()
            >= absoluteCeilingHours) {
      throw new SessionExpiredException(
          "Absolute session ceiling reached", "SESSION_CEILING_REACHED");
    }

    if (token.getLastUsedAt() != null
        && Duration.between(token.getLastUsedAt(), Instant.now()).toHours() >= idleTimeoutHours) {
      throw new SessionExpiredException("Idle session expired", "SESSION_IDLE_EXPIRED");
    }

    if (!Boolean.TRUE.equals(user.getIsActive())) {
      throw new InactiveUserException("Account is deactivated");
    }

    Instant now = Instant.now();
    token.setLastUsedAt(now);
    token.setRevoked(true);
    refreshTokenRepository.save(token);

    RefreshToken newToken =
        RefreshToken.builder()
            .user(user)
            .tokenHash(hashRefreshToken(newRefreshPlain))
            .expiresAt(now.plusMillis(refreshTtlMs))
            .revoked(false)
            .createdAt(now)
            .sessionIssuedAt(token.getSessionIssuedAt())
            .familyId(token.getFamilyId())
            .parentTokenHash(token.getTokenHash())
            .deviceId(token.getDeviceId())
            .ipCreated(token.getIpCreated())
            .userAgentCreated(token.getUserAgentCreated())
            .ipLastSeen(truncateIp(clientIp))
            .userAgentLastSeen(truncateUserAgent(userAgent))
            .sessionGroupId(token.getSessionGroupId())
            .build();
    refreshTokenRepository.save(newToken);

    return new RotationResult(user, newRefreshPlain);
  }

  @Transactional
  public void revokeByPlain(String refreshTokenValue) {
    if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
      return;
    }
    String hash = hashRefreshToken(refreshTokenValue);
    refreshTokenRepository
        .findByTokenHash(hash)
        .ifPresent(
            t -> {
              t.setRevoked(true);
              refreshTokenRepository.save(t);
              auditService.log(
                  t.getUser().getId(),
                  "USER_LOGOUT",
                  "User",
                  t.getUser().getId(),
                  "Logout successful",
                  null);
            });
  }

  public Optional<RefreshToken> findValidByPlain(String refreshPlain) {
    if (refreshPlain == null || refreshPlain.isBlank()) {
      return Optional.empty();
    }
    return refreshTokenRepository.findByTokenHash(hashRefreshToken(refreshPlain));
  }

  private static String truncateIp(String ip) {
    if (ip == null) return null;
    return ip.length() > 45 ? ip.substring(0, 45) : ip;
  }

  private static String truncateUserAgent(String ua) {
    if (ua == null) return null;
    return ua.length() > 500 ? ua.substring(0, 500) : ua;
  }

  public record RotationResult(User user, String newRefreshPlain) {}
}
