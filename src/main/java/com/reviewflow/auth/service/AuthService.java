package com.reviewflow.auth.service;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.auth.AuthTimingConstants;
import com.reviewflow.auth.repository.RefreshTokenRepository;
import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.infrastructure.security.JwtService;
import com.reviewflow.infrastructure.ratelimit.RateLimitResult;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_LOGIN;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_REFRESH_IP;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_REFRESH_USER;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.exception.InactiveUserException;
import com.reviewflow.shared.exception.TooManyRequestsException;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.dto.response.AuthUserResponse;
import com.reviewflow.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenService refreshTokenService;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuditService auditService;
  private final RateLimitService rateLimitService;
  private final ReviewFlowMetrics metrics;
  private final HashidService hashidService;
  private final PasswordPolicyService passwordPolicyService;
  private final TokenVersionService tokenVersionService;
  private final LoginLockoutService loginLockoutService;
  private final SessionPolicyResolver sessionPolicyResolver;

  @Transactional
  public LoginResult login(
      String email, String password, String ipAddress, String deviceIdHeader, String userAgent) {
    String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    passwordPolicyService.validateLoginInputBounds(password);

    RateLimitResult loginProbe = rateLimitService.probe(ipAddress, AUTH_LOGIN, null);
    if (!loginProbe.allowed()) {
      metrics.recordLoginRateLimited();
      throw new TooManyRequestsException(
          "Too many login attempts. Please try again later.", loginProbe.retryAfterSeconds());
    }

    Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
    if (userOpt.isEmpty()) {
      passwordEncoder.matches(password, AuthTimingConstants.DUMMY_PASSWORD_HASH);
      rateLimitService.consumeOnFailure(ipAddress, AUTH_LOGIN, null);
      auditService.log(
          null,
          "USER_LOGIN_FAILED",
          "User",
          null,
          "Email: " + normalizedEmail,
          ipAddress);
      throw new BadCredentialsException("Invalid credentials");
    }

    User user = userOpt.get();

    if (loginLockoutService.isLocked(user)) {
      loginLockoutService.auditLoginDuringLockout(user, ipAddress);
      rateLimitService.consumeOnFailure(ipAddress, AUTH_LOGIN, null);
      throw new BadCredentialsException("Invalid credentials");
    }

    if (!Boolean.TRUE.equals(user.getIsActive())) {
      rateLimitService.consumeOnFailure(ipAddress, AUTH_LOGIN, null);
      auditService.log(
          user.getId(),
          "USER_LOGIN_FAILED",
          "User",
          user.getId(),
          "Account deactivated",
          ipAddress);
      throw new InactiveUserException("Account is deactivated");
    }

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      rateLimitService.consumeOnFailure(ipAddress, AUTH_LOGIN, null);
      metrics.recordFailedLogin();
      loginLockoutService.recordLoginFailure(user, ipAddress);
      auditService.log(
          user.getId(), "USER_LOGIN_FAILED", "User", user.getId(), "Invalid password", ipAddress);
      throw new BadCredentialsException("Invalid credentials");
    }

    rateLimitService.reset(ipAddress, AUTH_LOGIN);
    metrics.recordUserLogin();
    loginLockoutService.clearFailures(user);

    SessionPolicyResolver.SessionPolicy policy = sessionPolicyResolver.resolveFor(user.getRole());
    ReviewFlowUserDetails details = new ReviewFlowUserDetails(user);
    int tokenVersion = tokenVersionService.getCurrentVersion(user.getId());
    String accessToken =
        jwtService.generateAccessToken(
            details, userAgent, tokenVersion, null, policy.accessTtlMs());
    String refreshTokenValue = jwtService.generateRefreshToken(policy.refreshTtlMs());
    Instant now = Instant.now();
    Instant expiresAt = now.plusMillis(policy.refreshTtlMs());
    String boundDeviceId =
        deviceIdHeader == null || deviceIdHeader.isBlank()
            ? UUID.randomUUID().toString()
            : deviceIdHeader.trim();
    refreshTokenService.createLoginSession(
        user, refreshTokenValue, now, expiresAt, ipAddress, userAgent, boundDeviceId);

    auditService.log(
        user.getId(), "USER_LOGIN", "User", user.getId(), "Login successful", ipAddress);

    AuthUserResponse userResponse =
        AuthUserResponse.builder()
            .userId(hashidService.encode(user.getId()))
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .emailNotificationsEnabled(user.getEmailNotificationsEnabled())
            .isActive(user.getIsActive())
            .role(user.getRole())
            .deviceId(boundDeviceId)
            .build();

    return new LoginResult(
        userResponse, accessToken, refreshTokenValue, policy.refreshTtlMs(), policy.accessTtlMs());
  }

  public record LoginResult(
      AuthUserResponse user,
      String accessToken,
      String refreshToken,
      long refreshTtlMs,
      long accessTtlMs) {}

  public long getRefreshExpirationMs() {
    return jwtService.getRefreshExpirationMs();
  }

  public long getAccessExpirationMs() {
    return jwtService.getAccessExpirationMs();
  }

  public RefreshResult refresh(
      String refreshTokenValue, String clientIp, String userAgent) {
    if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
      throw new BadCredentialsException("Invalid refresh token");
    }

    RateLimitResult refreshIpProbe = rateLimitService.probe(clientIp, AUTH_REFRESH_IP, null);
    if (!refreshIpProbe.allowed()) {
      throw new TooManyRequestsException(
          "Too many refresh attempts. Please try again later.", refreshIpProbe.retryAfterSeconds());
    }

    String hash = RefreshTokenService.hashRefreshToken(refreshTokenValue);
    User user =
        refreshTokenRepository
            .findByTokenHash(hash)
            .map(t -> t.getUser())
            .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

    String userIdKey = String.valueOf(user.getId());
    RateLimitResult refreshUserProbe =
        rateLimitService.probe(userIdKey, AUTH_REFRESH_USER, user.getRole());
    if (!refreshUserProbe.allowed()) {
      throw new TooManyRequestsException(
          "Too many refresh attempts. Please try again later.",
          refreshUserProbe.retryAfterSeconds());
    }

    rateLimitService.tryConsume(clientIp, AUTH_REFRESH_IP, null);
    rateLimitService.tryConsume(userIdKey, AUTH_REFRESH_USER, user.getRole());

    SessionPolicyResolver.SessionPolicy policy = sessionPolicyResolver.resolveFor(user.getRole());
    String newRefreshPlain = jwtService.generateRefreshToken(policy.refreshTtlMs());
    RefreshTokenService.RotationResult rotated =
        refreshTokenService.rotate(
            refreshTokenValue,
            newRefreshPlain,
            clientIp,
            userAgent,
            policy.refreshTtlMs(),
            policy.absoluteCeilingHours(),
            policy.idleTimeoutHours());

    ReviewFlowUserDetails details = new ReviewFlowUserDetails(rotated.user());
    int currentVersion = tokenVersionService.getCurrentVersion(rotated.user().getId());
    String newAccessToken =
        jwtService.generateAccessToken(
            details, userAgent, currentVersion, null, policy.accessTtlMs());

    return new RefreshResult(
        newAccessToken, rotated.newRefreshPlain(), policy.refreshTtlMs(), policy.accessTtlMs());
  }

  public record RefreshResult(
      String accessToken, String refreshToken, long refreshTtlMs, long accessTtlMs) {}

  public void logout(String refreshTokenValue) {
    refreshTokenService.revokeByPlain(refreshTokenValue);
  }
}
