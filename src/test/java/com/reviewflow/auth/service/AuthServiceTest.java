package com.reviewflow.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.auth.exception.SessionExpiredException;
import com.reviewflow.auth.repository.RefreshTokenRepository;
import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.infrastructure.security.JwtService;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import com.reviewflow.infrastructure.ratelimit.RateLimitTestFixtures;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_LOGIN;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_REFRESH_IP;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_REFRESH_USER;
import com.reviewflow.shared.domain.RefreshToken;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.ValidationException;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private AuditService auditService;
  @Mock private RateLimitService rateLimitService;
  @Mock private ReviewFlowMetrics metrics;
  @Mock private HashidService hashidService;
  @Mock private PasswordPolicyService passwordPolicyService;
  @Mock private TokenVersionService tokenVersionService;
  @Mock private LoginLockoutService loginLockoutService;
  @Mock private SessionPolicyResolver sessionPolicyResolver;

  @InjectMocks private AuthService authService;

  private static SessionPolicyResolver.SessionPolicy defaultPolicy() {
    return new SessionPolicyResolver.SessionPolicy(2, 12, 900_000L, 604_800_000L, false);
  }

  @Test
  void login_trimsAndLowercasesEmailBeforeLookup() {
    User user =
        User.builder()
            .id(1L)
            .email("admin@reviewflow.com")
            .passwordHash("hash")
            .firstName("Admin")
            .lastName("User")
            .role(UserRole.ADMIN)
            .isActive(true)
            .emailNotificationsEnabled(true)
            .build();

    when(rateLimitService.probe("127.0.0.1", AUTH_LOGIN, null))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_LOGIN));
    when(userRepository.findByEmail("admin@reviewflow.com")).thenReturn(Optional.of(user));
    when(loginLockoutService.isLocked(user)).thenReturn(false);
    when(passwordEncoder.matches("Test@1234", "hash")).thenReturn(true);
    when(tokenVersionService.getCurrentVersion(1L)).thenReturn(0);
    when(sessionPolicyResolver.resolveFor(UserRole.ADMIN)).thenReturn(defaultPolicy());
    when(jwtService.generateAccessToken(any(), any(), anyInt(), isNull(), any()))
        .thenReturn("access-token");
    when(jwtService.generateRefreshToken(anyLong())).thenReturn("refresh-token");
    when(hashidService.encode(1L)).thenReturn("U1HASH");
    when(refreshTokenService.createLoginSession(
            eq(user),
            eq("refresh-token"),
            any(Instant.class),
            any(Instant.class),
            eq("127.0.0.1"),
            isNull(),
            anyString()))
        .thenAnswer(
            inv ->
                RefreshToken.builder()
                    .id(1L)
                    .sessionGroupId(1L)
                    .familyId("fam")
                    .user(inv.getArgument(0, User.class))
                    .tokenHash("x")
                    .expiresAt(Instant.now().plusSeconds(600))
                    .revoked(false)
                    .createdAt(Instant.now())
                    .sessionIssuedAt(Instant.now())
                    .build());

    AuthService.LoginResult result =
        authService.login("  ADMIN@ReviewFlow.com  ", "Test@1234", "127.0.0.1", null, null);

    assertNotNull(result);
    assertEquals("admin@reviewflow.com", result.user().getEmail());
    verify(userRepository).findByEmail("admin@reviewflow.com");
    verify(passwordEncoder).matches("Test@1234", "hash");
    verify(passwordPolicyService).validateLoginInputBounds("Test@1234");
    verify(metrics).recordLoginResult("success");
    verify(loginLockoutService).clearFailures(user);
    verify(auditService)
        .logSecurityEvent(1L, "USER_LOGIN", "User", 1L, "Login successful", "127.0.0.1");
  }

  @Test
  void login_doesNotTrimPassword() {
    User user =
        User.builder()
            .id(2L)
            .email("admin@reviewflow.com")
            .passwordHash("hash")
            .firstName("Admin")
            .lastName("User")
            .role(UserRole.ADMIN)
            .isActive(true)
            .emailNotificationsEnabled(true)
            .build();

    when(rateLimitService.probe("127.0.0.1", AUTH_LOGIN, null))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_LOGIN));
    when(userRepository.findByEmail("admin@reviewflow.com")).thenReturn(Optional.of(user));
    when(loginLockoutService.isLocked(user)).thenReturn(false);
    when(passwordEncoder.matches("  Test@1234  ", "hash")).thenReturn(false);

    try {
      authService.login("  admin@reviewflow.com  ", "  Test@1234  ", "127.0.0.1", null, null);
      fail("Expected BadCredentialsException");
    } catch (BadCredentialsException ex) {
      assertNotNull(ex);
    }

    verify(passwordEncoder).matches("  Test@1234  ", "hash");
    verify(passwordEncoder, never()).matches("Test@1234", "hash");
    verify(passwordPolicyService).validateLoginInputBounds("  Test@1234  ");
    verify(metrics).recordLoginResult("failed");
    verify(loginLockoutService).recordLoginFailure(user, "127.0.0.1");
    verify(auditService)
        .logSecurityEvent(2L, "USER_LOGIN_FAILED", "User", 2L, "Invalid password", "127.0.0.1");
  }

  @Test
  void login_whenPasswordExceedsBounds_throwsValidationExceptionBeforeLookup() {
    doThrow(
            new ValidationException(
                "Password must be between 8 and 64 characters", "VALIDATION_ERROR"))
        .when(passwordPolicyService)
        .validateLoginInputBounds("x".repeat(65));

    try {
      authService.login("admin@reviewflow.com", "x".repeat(65), "127.0.0.1", null, null);
      fail("Expected ValidationException");
    } catch (ValidationException ex) {
      assertNotNull(ex);
      assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    verify(passwordPolicyService).validateLoginInputBounds("x".repeat(65));
    verify(userRepository, never()).findByEmail(any());
    verify(passwordEncoder, never()).matches(any(), any());
  }

  @Test
  void refresh_whenSessionCeilingReached_throwsSessionExpiredException() {
    String refreshToken = "valid-refresh";
    User tokenUser =
        User.builder()
            .id(99L)
            .email("u@x.com")
            .passwordHash("h")
            .firstName("A")
            .lastName("B")
            .role(UserRole.STUDENT)
            .isActive(true)
            .emailNotificationsEnabled(true)
            .build();
    RefreshToken token = new RefreshToken();
    token.setUser(tokenUser);
    token.setSessionIssuedAt(java.time.Instant.now().minus(java.time.Duration.ofHours(13)));
    token.setRevoked(false);
    token.setExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(1)));

    when(rateLimitService.probe(anyString(), eq(AUTH_REFRESH_IP), isNull()))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_REFRESH_IP));
    when(rateLimitService.probe(eq("99"), eq(AUTH_REFRESH_USER), any()))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_REFRESH_USER));
    when(rateLimitService.tryConsume(anyString(), eq(AUTH_REFRESH_IP), isNull()))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_REFRESH_IP));
    when(rateLimitService.tryConsume(eq("99"), eq(AUTH_REFRESH_USER), any()))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_REFRESH_USER));
    when(sessionPolicyResolver.resolveFor(UserRole.STUDENT)).thenReturn(defaultPolicy());
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    when(jwtService.generateRefreshToken(604800000L)).thenReturn("new-refresh");
    when(refreshTokenService.rotate(
            eq(refreshToken),
            eq("new-refresh"),
            eq("127.0.0.1"),
            isNull(),
            anyLong(),
            anyInt(),
            anyInt()))
        .thenThrow(
            new SessionExpiredException("Absolute session ceiling reached", "SESSION_CEILING_REACHED"));

    SessionExpiredException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            SessionExpiredException.class,
            () -> authService.refresh(refreshToken, "127.0.0.1", null));

    assertEquals("SESSION_CEILING_REACHED", ex.getErrorCode());
  }

  @Test
  void refresh_whenIdleTimeoutReached_throwsSessionExpiredException() {
    String refreshToken = "valid-refresh";
    User tokenUser =
        User.builder()
            .id(99L)
            .email("u@x.com")
            .passwordHash("h")
            .firstName("A")
            .lastName("B")
            .role(UserRole.STUDENT)
            .isActive(true)
            .emailNotificationsEnabled(true)
            .build();
    RefreshToken token = new RefreshToken();
    token.setUser(tokenUser);
    token.setSessionIssuedAt(java.time.Instant.now().minus(java.time.Duration.ofHours(1)));
    token.setLastUsedAt(java.time.Instant.now().minus(java.time.Duration.ofHours(3)));
    token.setRevoked(false);
    token.setExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(1)));

    when(rateLimitService.probe(anyString(), eq(AUTH_REFRESH_IP), isNull()))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_REFRESH_IP));
    when(rateLimitService.probe(eq("99"), eq(AUTH_REFRESH_USER), any()))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_REFRESH_USER));
    when(rateLimitService.tryConsume(anyString(), eq(AUTH_REFRESH_IP), isNull()))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_REFRESH_IP));
    when(rateLimitService.tryConsume(eq("99"), eq(AUTH_REFRESH_USER), any()))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_REFRESH_USER));
    when(sessionPolicyResolver.resolveFor(UserRole.STUDENT)).thenReturn(defaultPolicy());
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    when(jwtService.generateRefreshToken(604800000L)).thenReturn("new-refresh");
    when(refreshTokenService.rotate(
            eq(refreshToken),
            eq("new-refresh"),
            eq("127.0.0.1"),
            isNull(),
            anyLong(),
            anyInt(),
            anyInt()))
        .thenThrow(new SessionExpiredException("Idle session expired", "SESSION_IDLE_EXPIRED"));

    SessionExpiredException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            SessionExpiredException.class,
            () -> authService.refresh(refreshToken, "127.0.0.1", null));

    assertEquals("SESSION_IDLE_EXPIRED", ex.getErrorCode());
  }
}
