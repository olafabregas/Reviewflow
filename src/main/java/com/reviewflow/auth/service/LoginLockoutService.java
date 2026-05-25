package com.reviewflow.auth.service;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginLockoutService {

  private final UserRepository userRepository;
  private final AuditService auditService;

  @Value("${auth.lockout.threshold:10}")
  private int threshold;

  @Value("${auth.lockout.window-minutes:15}")
  private int windowMinutes;

  @Value("${auth.lockout.duration-minutes:15}")
  private int durationMinutes;

  public boolean isLocked(User user) {
    if (user.getLockedUntil() == null) {
      return false;
    }
    return user.getLockedUntil().isAfter(Instant.now());
  }

  @Transactional
  public void recordLoginFailure(User user, String ipAddress) {
    Instant now = Instant.now();
    Long userId = user.getId();

    if (user.getLastFailedLoginAt() == null
        || user.getLastFailedLoginAt().plus(windowMinutes, ChronoUnit.MINUTES).isBefore(now)) {
      userRepository.resetFailedLoginCount(userId, now);
    } else {
      userRepository.incrementFailedLoginCount(userId, now);
    }

    User refreshed =
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));

    if (refreshed.getFailedLoginCount() != null && refreshed.getFailedLoginCount() >= threshold) {
      Instant lockedUntil = now.plus(durationMinutes, ChronoUnit.MINUTES);
      userRepository.lockUser(userId, lockedUntil);
      auditService.logSecurityEvent(
          userId,
          "ACCOUNT_LOCKED",
          "User",
          userId,
          "Too many failed login attempts",
          ipAddress);
    }
  }

  @Transactional
  public void clearFailures(User user) {
    user.setFailedLoginCount(0);
    user.setLockedUntil(null);
    user.setLastFailedLoginAt(null);
    userRepository.save(user);
  }

  @Transactional
  public void auditLoginDuringLockout(User user, String ipAddress) {
    auditService.logSecurityEvent(
        user.getId(),
        "LOGIN_DURING_LOCKOUT",
        "User",
        user.getId(),
        "Attempt while account locked",
        ipAddress);
  }
}
