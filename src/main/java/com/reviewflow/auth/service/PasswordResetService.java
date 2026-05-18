package com.reviewflow.auth.service;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.auth.repository.PasswordResetTokenRepository;
import com.reviewflow.auth.repository.RefreshTokenRepository;
import com.reviewflow.infrastructure.email.event.PasswordResetCompletedEmailEvent;
import com.reviewflow.infrastructure.email.event.PasswordResetRequestedEmailEvent;
import com.reviewflow.shared.domain.PasswordResetToken;
import com.reviewflow.shared.domain.User;
import com.reviewflow.infrastructure.ratelimit.RateLimitResult;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_PASSWORD_RESET_CONFIRM_IP;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_PASSWORD_RESET_EMAIL;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_PASSWORD_RESET_REQUEST_IP;
import com.reviewflow.shared.exception.TooManyRequestsException;
import com.reviewflow.shared.exception.ValidationException;
import com.reviewflow.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyService passwordPolicyService;
  private final TokenVersionService tokenVersionService;
  private final UserDetailsCacheService userDetailsCacheService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final RateLimitService rateLimitService;

  @Value("${app.base-url:http://localhost:5173}")
  private String appBaseUrl;

  @Value("${auth.password-reset.token-validity-minutes:30}")
  private int tokenValidityMinutes;

  @Transactional
  public void requestReset(String email, String ip) {
    RateLimitResult requestIp = rateLimitService.tryConsume(ip, AUTH_PASSWORD_RESET_REQUEST_IP, null);
    if (!requestIp.allowed()) {
      throw new TooManyRequestsException(
          "Too many password reset requests. Please try again later.", requestIp.retryAfterSeconds());
    }
    String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    if (!normalized.isEmpty()) {
      RateLimitResult requestEmail =
          rateLimitService.tryConsume(normalized, AUTH_PASSWORD_RESET_EMAIL, null);
      if (!requestEmail.allowed()) {
        throw new TooManyRequestsException(
            "Too many password reset requests. Please try again later.",
            requestEmail.retryAfterSeconds());
      }
    }

    Optional<User> userOpt = userRepository.findByEmail(normalized);
    if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getIsActive())) {
      return;
    }

    User user = userOpt.get();
    passwordResetTokenRepository.deletePendingForUser(user.getId());

    byte[] raw = new byte[32];
    new SecureRandom().nextBytes(raw);
    String plain = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    String tokenHash = sha256Hex(plain);
    Instant now = Instant.now();
    Instant expires = now.plus(tokenValidityMinutes, ChronoUnit.MINUTES);

    PasswordResetToken entity =
        PasswordResetToken.builder()
            .user(user)
            .tokenHash(tokenHash)
            .createdAt(now)
            .expiresAt(expires)
            .consumedAt(null)
            .ipRequested(ip == null ? "" : ip)
            .build();
    passwordResetTokenRepository.save(entity);

    String resetUrl =
        UriComponentsBuilder.fromUriString(baseUrlNoSlash())
            .path("/reset-password")
            .queryParam("token", plain)
            .build()
            .toUriString();

    eventPublisher.publishEvent(
        new PasswordResetRequestedEmailEvent(user.getEmail(), user.getFirstName(), resetUrl));

    auditService.log(
        user.getId(),
        "PASSWORD_RESET_REQUESTED",
        "User",
        user.getId(),
        Map.of("ip", ip == null ? "" : ip),
        ip);
  }

  @Transactional
  public void confirm(String plainToken, String newPassword, String ip) {
    RateLimitResult confirmIp = rateLimitService.tryConsume(ip, AUTH_PASSWORD_RESET_CONFIRM_IP, null);
    if (!confirmIp.allowed()) {
      throw new TooManyRequestsException(
          "Too many attempts. Please try again later.", confirmIp.retryAfterSeconds());
    }

    passwordPolicyService.validateLoginInputBounds(newPassword);

    if (plainToken == null || plainToken.isBlank()) {
      throw new ValidationException("Invalid or expired reset link", "INVALID_RESET_TOKEN");
    }

    String tokenHash = sha256Hex(plainToken.trim());
    PasswordResetToken row =
        passwordResetTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () -> new ValidationException("Invalid or expired reset link", "INVALID_RESET_TOKEN"));

    if (row.getConsumedAt() != null || row.getExpiresAt().isBefore(Instant.now())) {
      throw new ValidationException("Invalid or expired reset link", "INVALID_RESET_TOKEN");
    }

    User user = row.getUser();
    passwordPolicyService.validateForCreateOrUpdate(newPassword, user.getEmail());

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);

    row.setConsumedAt(Instant.now());
    passwordResetTokenRepository.save(row);

    refreshTokenRepository.revokeAllForUser(user.getId());
    userRepository.incrementTokenVersion(user.getId());
    tokenVersionService.invalidate(user.getId());
    userDetailsCacheService.evict(user.getEmail());

    eventPublisher.publishEvent(
        new PasswordResetCompletedEmailEvent(user.getEmail(), user.getFirstName()));

    auditService.log(
        user.getId(),
        "PASSWORD_RESET_COMPLETED",
        "User",
        user.getId(),
        Map.of("ip", ip == null ? "" : ip),
        ip);
  }

  private String baseUrlNoSlash() {
    String u = appBaseUrl == null || appBaseUrl.isBlank() ? "http://localhost:5173" : appBaseUrl.trim();
    while (u.endsWith("/")) {
      u = u.substring(0, u.length() - 1);
    }
    return u;
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
