package com.reviewflow.auth.service;

import com.reviewflow.auth.AuthTimingConstants;
import com.reviewflow.infrastructure.security.JwtService;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.domain.User;
import com.reviewflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StepUpService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final TokenVersionService tokenVersionService;
  private final PasswordPolicyService passwordPolicyService;
  private final LoginLockoutService loginLockoutService;

  @Transactional
  public String completeStepUp(Long userId, String password, String ip, String userAgent) {
    passwordPolicyService.validateLoginInputBounds(password);
    User user =
        userRepository.findById(userId).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

    if (loginLockoutService.isLocked(user)) {
      loginLockoutService.auditLoginDuringLockout(user, ip);
      throw new BadCredentialsException("Invalid credentials");
    }

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      loginLockoutService.recordLoginFailure(user, ip);
      passwordEncoder.matches(password, AuthTimingConstants.DUMMY_PASSWORD_HASH);
      throw new BadCredentialsException("Invalid credentials");
    }

    loginLockoutService.clearFailures(user);

    int ver = tokenVersionService.getCurrentVersion(userId);
    long stepUpAt = java.time.Instant.now().getEpochSecond();
    ReviewFlowUserDetails details = new ReviewFlowUserDetails(user);
    return jwtService.generateAccessToken(details, userAgent, ver, stepUpAt);
  }
}
