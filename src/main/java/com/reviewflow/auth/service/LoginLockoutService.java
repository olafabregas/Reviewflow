package com.reviewflow.auth.service;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLockoutService {

  private final UserRepository userRepository;
  private final AuditService auditService;
  private final ReviewFlowMetrics metrics;
  private final HashidService hashidService;

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
    int count = user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount();
    if (user.getLastFailedLoginAt() == null
        || user.getLastFailedLoginAt().plus(windowMinutes, ChronoUnit.MINUTES).isBefore(now)) {
      count = 1;
    } else {
      count = count + 1;
    }
    user.setFailedLoginCount(count);
    user.setLastFailedLoginAt(now);
    if (count >= threshold) {
      Instant lockedUntil = now.plus(durationMinutes, ChronoUnit.MINUTES);
      user.setLockedUntil(lockedUntil);
      log.warn(
          "Account locked userId={} ipAddress={} lockedUntil={}",
          hashidService.encode(user.getId()),
          ipAddress,
          lockedUntil);
      metrics.recordLockout();
      auditService.log(
          user.getId(),
          "ACCOUNT_LOCKED",
          "User",
          user.getId(),
          "Too many failed login attempts",
          ipAddress);
    }
    userRepository.save(user);
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
    auditService.log(
        user.getId(),
        "LOGIN_DURING_LOCKOUT",
        "User",
        user.getId(),
        "Attempt while account locked",
        ipAddress);
  }
}
