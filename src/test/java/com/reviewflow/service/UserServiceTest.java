package com.reviewflow.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.reviewflow.exception.BusinessRuleException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.entity.User;
import com.reviewflow.repository.*;
import com.reviewflow.storage.StorageService;
import com.reviewflow.util.HashidService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private TokenVersionService tokenVersionService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private TeamMemberRepository teamMemberRepository;
  @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
  @Mock private CourseInstructorRepository courseInstructorRepository;
  @Mock private FileSecurityValidator fileSecurityValidator;
  @Mock private EXIFStripperService exifStripperService;
  @Mock private StorageService storageService;
  @Mock private AuditService auditService;
  @Mock private AdminStatsService adminStatsService;
  @Mock private HashidService hashidService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PasswordPolicyService passwordPolicyService;

  @InjectMocks private UserService userService;

  @Test
  void deactivateUser_ShouldRevokeTokensAndIncrementVersion() {
    Long targetId = 1L;
    Long adminId = 2L;
    User user = User.builder().id(targetId).isActive(true).build();

    when(userRepository.findById(targetId)).thenReturn(Optional.of(user));
    when(refreshTokenRepository.revokeAllForUser(targetId)).thenReturn(3);

    userService.deactivateUser(targetId, adminId);

    assertFalse(user.getIsActive());
    verify(refreshTokenRepository).revokeAllForUser(targetId);
    verify(userRepository).incrementTokenVersion(targetId);
    verify(tokenVersionService).evict(targetId);
    verify(auditService).log(eq(adminId), eq("USER_DEACTIVATED"), eq("User"), eq(targetId), argThat((Map<String, Object> map) -> 
        map.get("refreshTokensRevoked").equals(3) && map.get("tokenVersionBumped").equals(true)
    ), isNull());
  }

  @Test
  void deactivateUser_ShouldThrowException_WhenDeactivatingSelf() {
    Long userId = 1L;
    assertThrows(BusinessRuleException.class, () -> userService.deactivateUser(userId, userId));
  }

  @Test
  void deactivateUser_ShouldThrowException_WhenAlreadyInactive() {
    Long targetId = 1L;
    Long adminId = 2L;
    User user = User.builder().id(targetId).isActive(false).build();

    when(userRepository.findById(targetId)).thenReturn(Optional.of(user));

    assertThrows(BusinessRuleException.class, () -> userService.deactivateUser(targetId, adminId));
  }
}
