package com.reviewflow.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoginLockoutServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private AuditService auditService;

  @InjectMocks private LoginLockoutService loginLockoutService;

  @Test
  void recordLoginFailure_resetsCountWhenOutsideWindow() {
    ReflectionTestUtils.setField(loginLockoutService, "threshold", 5);
    ReflectionTestUtils.setField(loginLockoutService, "windowMinutes", 15);

    User user =
        User.builder()
            .id(1L)
            .email("a@b.com")
            .passwordHash("h")
            .firstName("A")
            .lastName("B")
            .role(UserRole.STUDENT)
            .isActive(true)
            .emailNotificationsEnabled(true)
            .failedLoginCount(9)
            .lastFailedLoginAt(Instant.now().minus(20, ChronoUnit.MINUTES))
            .build();

    loginLockoutService.recordLoginFailure(user, "1.1.1.1");

    assertEquals(1, user.getFailedLoginCount());
    verify(auditService, never())
        .log(any(), eq("ACCOUNT_LOCKED"), any(), any(), anyString(), anyString());
  }

  @Test
  void recordLoginFailure_incrementsWithinWindow() {
    ReflectionTestUtils.setField(loginLockoutService, "threshold", 5);
    ReflectionTestUtils.setField(loginLockoutService, "windowMinutes", 15);

    Instant lastFail = Instant.now().minus(1, ChronoUnit.MINUTES);
    User user =
        User.builder()
            .id(1L)
            .email("a@b.com")
            .passwordHash("h")
            .firstName("A")
            .lastName("B")
            .role(UserRole.STUDENT)
            .isActive(true)
            .emailNotificationsEnabled(true)
            .failedLoginCount(2)
            .lastFailedLoginAt(lastFail)
            .build();

    loginLockoutService.recordLoginFailure(user, "1.1.1.1");

    assertEquals(3, user.getFailedLoginCount());
  }

  @Test
  void recordLoginFailure_emitsAuditWhenThresholdReached() {
    ReflectionTestUtils.setField(loginLockoutService, "threshold", 3);
    ReflectionTestUtils.setField(loginLockoutService, "windowMinutes", 15);
    ReflectionTestUtils.setField(loginLockoutService, "durationMinutes", 30);

    User user =
        User.builder()
            .id(7L)
            .email("a@b.com")
            .passwordHash("h")
            .firstName("A")
            .lastName("B")
            .role(UserRole.STUDENT)
            .isActive(true)
            .emailNotificationsEnabled(true)
            .failedLoginCount(2)
            .lastFailedLoginAt(Instant.now().minus(1, ChronoUnit.MINUTES))
            .build();

    loginLockoutService.recordLoginFailure(user, "9.9.9.9");

    assertEquals(3, user.getFailedLoginCount());
    assertTrue(user.getLockedUntil().isAfter(Instant.now()));
    verify(auditService)
        .log(7L, "ACCOUNT_LOCKED", "User", 7L, "Too many failed login attempts", "9.9.9.9");
  }

  @Test
  void isLocked_falseWhenNullOrExpired() {
    User open = User.builder().lockedUntil(null).build();
    assertFalse(loginLockoutService.isLocked(open));

    User expired = User.builder().lockedUntil(Instant.now().minusSeconds(1)).build();
    assertFalse(loginLockoutService.isLocked(expired));
  }

  @Test
  void clearFailures_zerosFields() {
    User user =
        User.builder()
            .failedLoginCount(5)
            .lockedUntil(Instant.now().plusSeconds(60))
            .lastFailedLoginAt(Instant.now())
            .build();

    loginLockoutService.clearFailures(user);

    verify(userRepository).save(user);
    assertEquals(0, user.getFailedLoginCount());
    assertTrue(user.getLockedUntil() == null);
    assertTrue(user.getLastFailedLoginAt() == null);
  }
}
