package com.reviewflow.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.auth.repository.PasswordResetTokenRepository;
import com.reviewflow.auth.repository.RefreshTokenRepository;
import com.reviewflow.infrastructure.email.event.PasswordResetCompletedEmailEvent;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import com.reviewflow.infrastructure.ratelimit.RateLimitTestFixtures;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_PASSWORD_RESET_CONFIRM_IP;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_PASSWORD_RESET_EMAIL;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_PASSWORD_RESET_REQUEST_IP;
import com.reviewflow.shared.domain.PasswordResetToken;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private PasswordPolicyService passwordPolicyService;
  @Mock private TokenVersionService tokenVersionService;
  @Mock private UserDetailsCacheService userDetailsCacheService;
  @Mock private AuditService auditService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private RateLimitService rateLimitService;

  @InjectMocks private PasswordResetService passwordResetService;

  @Test
  void requestReset_unknownEmail_doesNotPublishOrSave() {
    when(rateLimitService.tryConsume("1.1.1.1", AUTH_PASSWORD_RESET_REQUEST_IP, null))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_PASSWORD_RESET_REQUEST_IP));
    when(rateLimitService.tryConsume("x@y.com", AUTH_PASSWORD_RESET_EMAIL, null))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_PASSWORD_RESET_EMAIL));
    when(userRepository.findByEmail("x@y.com")).thenReturn(Optional.empty());

    passwordResetService.requestReset("x@y.com", "1.1.1.1");

    verify(passwordResetTokenRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
    verify(auditService, never()).log(any(), any(), any(), any(), anyString(), any());
  }

  @Test
  void confirm_validToken_updatesPasswordAndInvalidatesSessions() {
    User user =
        User.builder()
            .id(77L)
            .email("user@example.com")
            .passwordHash("old-hash")
            .firstName("Pat")
            .lastName("Example")
            .role(UserRole.STUDENT)
            .isActive(true)
            .tokenVersion(4)
            .build();
    String token = "reset-token-123";
    String tokenHash = sha256Hex(token);
    PasswordResetToken row =
        PasswordResetToken.builder()
            .id(12L)
            .user(user)
            .tokenHash(tokenHash)
            .createdAt(Instant.now().minusSeconds(60))
            .expiresAt(Instant.now().plusSeconds(600))
            .consumedAt(null)
            .ipRequested("198.51.100.1")
            .build();

    when(rateLimitService.tryConsume("2.2.2.2", AUTH_PASSWORD_RESET_CONFIRM_IP, null))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_PASSWORD_RESET_CONFIRM_IP));
    when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(row));
    when(passwordEncoder.encode("NewP@ssw0rd!")).thenReturn("encoded-password");

    passwordResetService.confirm(token, "NewP@ssw0rd!", "2.2.2.2");

    assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
    assertThat(row.getConsumedAt()).isNotNull();
    verify(passwordPolicyService).validateLoginInputBounds("NewP@ssw0rd!");
    verify(passwordPolicyService).validateForCreateOrUpdate("NewP@ssw0rd!", "user@example.com");
    verify(userRepository).save(user);
    verify(passwordResetTokenRepository).save(row);
    verify(refreshTokenRepository).revokeAllForUser(77L);
    verify(userRepository).incrementTokenVersion(77L);
    verify(tokenVersionService).invalidate(77L);
    verify(userDetailsCacheService).evict("user@example.com");
    verify(eventPublisher).publishEvent(any(PasswordResetCompletedEmailEvent.class));
    verify(auditService)
        .log(eq(77L), eq("PASSWORD_RESET_COMPLETED"), eq("User"), eq(77L), anyMap(), eq("2.2.2.2"));
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

