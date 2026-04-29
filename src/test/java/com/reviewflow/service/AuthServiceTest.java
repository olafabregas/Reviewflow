package com.reviewflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.shared.exception.ValidationException;
import com.reviewflow.model.entity.RefreshToken;
import com.reviewflow.model.entity.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.infra.monitoring.ReviewFlowMetrics;
import com.reviewflow.repository.RefreshTokenRepository;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.infra.security.JwtService;
import com.reviewflow.shared.util.HashidService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private AuditService auditService;
  @Mock private RateLimiterService rateLimiterService;
  @Mock private ReviewFlowMetrics metrics;
  @Mock private HashidService hashidService;
  @Mock private PasswordPolicyService passwordPolicyService;
  @Mock private TokenVersionService tokenVersionService;

  @InjectMocks private AuthService authService;

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

    when(rateLimiterService.isLoginRateLimited("127.0.0.1")).thenReturn(false);
    when(userRepository.findByEmail("admin@reviewflow.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Test@1234", "hash")).thenReturn(true);
    when(tokenVersionService.getCurrentVersion(1L)).thenReturn(0);
    when(jwtService.generateAccessToken(any(), anyInt())).thenReturn("access-token");
    when(jwtService.generateRefreshToken()).thenReturn("refresh-token");
    when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L);
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(hashidService.encode(1L)).thenReturn("U1HASH");

    AuthService.LoginResult result =
        authService.login("  ADMIN@ReviewFlow.com  ", "Test@1234", "127.0.0.1");

    assertNotNull(result);
    assertEquals("admin@reviewflow.com", result.user().getEmail());
    verify(userRepository).findByEmail("admin@reviewflow.com");
    verify(passwordEncoder).matches("Test@1234", "hash");
    verify(passwordPolicyService).validateLoginInputBounds("Test@1234");
    verify(metrics).recordUserLogin();
    verify(auditService).log(1L, "USER_LOGIN", "User", 1L, "Login successful", "127.0.0.1");
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

    when(rateLimiterService.isLoginRateLimited("127.0.0.1")).thenReturn(false);
    when(userRepository.findByEmail("admin@reviewflow.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("  Test@1234  ", "hash")).thenReturn(false);

    try {
      authService.login("  admin@reviewflow.com  ", "  Test@1234  ", "127.0.0.1");
      fail("Expected BadCredentialsException");
    } catch (BadCredentialsException ex) {
      assertNotNull(ex);
    }

    verify(passwordEncoder).matches("  Test@1234  ", "hash");
    verify(passwordEncoder, never()).matches("Test@1234", "hash");
    verify(passwordPolicyService).validateLoginInputBounds("  Test@1234  ");
    verify(metrics).recordFailedLogin();
    verify(auditService).log(2L, "USER_LOGIN_FAILED", "User", 2L, "Invalid password", "127.0.0.1");
  }

  @Test
  void login_whenPasswordExceedsBounds_throwsValidationExceptionBeforeLookup() {
    doThrow(
            new ValidationException(
                "Password must be between 8 and 64 characters", "VALIDATION_ERROR"))
        .when(passwordPolicyService)
        .validateLoginInputBounds("x".repeat(65));

    try {
      authService.login("admin@reviewflow.com", "x".repeat(65), "127.0.0.1");
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
    ReflectionTestUtils.setField(authService, "absoluteCeilingHours", 12);
    String refreshToken = "valid-refresh";
    RefreshToken token = new RefreshToken();
    token.setSessionIssuedAt(java.time.Instant.now().minus(java.time.Duration.ofHours(13)));
    token.setRevoked(false);
    token.setExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(1)));

    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

    com.reviewflow.exception.SessionExpiredException ex = 
        org.junit.jupiter.api.Assertions.assertThrows(com.reviewflow.exception.SessionExpiredException.class, 
            () -> authService.refresh(refreshToken));

    assertEquals("SESSION_CEILING_REACHED", ex.getErrorCode());
  }

  @Test
  void refresh_whenIdleTimeoutReached_throwsSessionExpiredException() {
    ReflectionTestUtils.setField(authService, "absoluteCeilingHours", 12);
    ReflectionTestUtils.setField(authService, "idleTimeoutHours", 2);
    String refreshToken = "valid-refresh";
    RefreshToken token = new RefreshToken();
    token.setSessionIssuedAt(java.time.Instant.now().minus(java.time.Duration.ofHours(1)));
    token.setLastUsedAt(java.time.Instant.now().minus(java.time.Duration.ofHours(3)));
    token.setRevoked(false);
    token.setExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(1)));

    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

    com.reviewflow.exception.SessionExpiredException ex = 
        org.junit.jupiter.api.Assertions.assertThrows(com.reviewflow.exception.SessionExpiredException.class, 
            () -> authService.refresh(refreshToken));

    assertEquals("SESSION_IDLE_EXPIRED", ex.getErrorCode());
  }
}
