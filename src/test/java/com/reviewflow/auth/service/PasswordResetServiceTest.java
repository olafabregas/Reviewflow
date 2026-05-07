package com.reviewflow.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.auth.repository.PasswordResetTokenRepository;
import com.reviewflow.auth.repository.RefreshTokenRepository;
import com.reviewflow.infrastructure.security.RateLimiterService;
import com.reviewflow.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

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
  @Mock private RateLimiterService rateLimiterService;

  @InjectMocks private PasswordResetService passwordResetService;

  @Test
  void requestReset_unknownEmail_doesNotPublishOrSave() {
    when(rateLimiterService.isPasswordResetRequestIpRateLimited("1.1.1.1")).thenReturn(false);
    when(rateLimiterService.isPasswordResetRequestEmailRateLimited("x@y.com")).thenReturn(false);
    when(userRepository.findByEmail("x@y.com")).thenReturn(Optional.empty());

    passwordResetService.requestReset("x@y.com", "1.1.1.1");

    verify(passwordResetTokenRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
    verify(auditService, never()).log(any(), any(), any(), any(), anyString(), any());
  }
}
