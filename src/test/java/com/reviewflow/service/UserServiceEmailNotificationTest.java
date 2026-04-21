package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.reviewflow.event.email.AccountReactivatedEmailEvent;
import com.reviewflow.event.email.WelcomeEmailEvent;
import com.reviewflow.exception.BusinessRuleException;
import com.reviewflow.exception.DuplicateResourceException;
import com.reviewflow.exception.ValidationException;
import com.reviewflow.model.dto.response.AuthUserResponse;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.repository.UserRepository;
import com.reviewflow.storage.StorageService;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unused")
class UserServiceEmailNotificationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private CourseInstructorRepository courseInstructorRepository;
    @Mock
    private FileSecurityValidator fileSecurityValidator;
    @Mock
    private EXIFStripperService exifStripperService;
    @Mock
    private StorageService storageService;
    @Mock
    private AuditService auditService;
    @Mock
    private AdminStatsService adminStatsService;
    @Mock
    private HashidService hashidService;
    @Mock
    private PasswordPolicyService passwordPolicyService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    @Test
    void updateEmailPreference_happyPath_updatesAndReturnsMappedField() {
        Long userId = 31L;
        User user = User.builder()
                .id(userId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .emailNotificationsEnabled(true)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hashidService.encode(userId)).thenReturn("U31HASH");

        AuthUserResponse response = userService.updateEmailPreference(userId, false);

        assertEquals(false, user.getEmailNotificationsEnabled());
        assertEquals(false, response.getEmailNotificationsEnabled());
        assertEquals("U31HASH", response.getUserId());
        verify(userRepository).save(user);
    }

    @Test
    void updateEmailPreference_nullValue_throwsValidationException() {
        ValidationException thrown = assertThrows(ValidationException.class,
                () -> userService.updateEmailPreference(32L, null));
        assertNotNull(thrown);
    }

    @Test
    void createUser_publishesWelcomeEmailEvent() {
        when(userRepository.findByEmail("new@test.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Strong@123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 40L);
            return saved;
        });

        User created = userService.createUser("new@test.local", "Strong@123", "Ada", "Lovelace", UserRole.STUDENT);

        assertNotNull(created);
        verify(passwordPolicyService).validateForCreateOrUpdate("Strong@123", "new@test.local");
        ArgumentCaptor<WelcomeEmailEvent> eventCaptor = ArgumentCaptor.forClass(WelcomeEmailEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("new@test.local", eventCaptor.getValue().getRecipientEmail());
        assertEquals("Ada", eventCaptor.getValue().getFirstName());
    }

    @Test
    void createUser_duplicateEmail_throwsDuplicateResourceException() {
        User existing = User.builder()
                .id(42L)
                .email("new@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();
        when(userRepository.findByEmail("new@test.local")).thenReturn(Optional.of(existing));

        DuplicateResourceException thrown = assertThrows(
                DuplicateResourceException.class,
                () -> userService.createUser("new@test.local", "password123", "Ada", "Lovelace", UserRole.STUDENT));
        assertNotNull(thrown);
        assertEquals("EMAIL_EXISTS", thrown.getCode());
        assertEquals("User with email already exists", thrown.getMessage());
    }

    @Test
    void createUser_shortPassword_throwsValidationException() {
        when(userRepository.findByEmail("new@test.local")).thenReturn(Optional.empty());
        doThrow(new ValidationException("Password must be between 8 and 64 characters", "VALIDATION_ERROR"))
                .when(passwordPolicyService).validateForCreateOrUpdate(eq("short"), eq("new@test.local"));

        ValidationException thrown = assertThrows(
                ValidationException.class,
                () -> userService.createUser("new@test.local", "short", "Ada", "Lovelace", UserRole.STUDENT));
        assertNotNull(thrown);
    }

    @Test
    void reactivateUser_publishesAccountReactivatedEmailEvent() {
        User user = User.builder()
                .id(41L)
                .email("inactive@test.local")
                .passwordHash("x")
                .firstName("Grace")
                .lastName("Hopper")
                .role(UserRole.STUDENT)
                .isActive(false)
                .build();

        when(userRepository.findById(41L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.reactivateUser(41L);

        ArgumentCaptor<AccountReactivatedEmailEvent> eventCaptor = ArgumentCaptor.forClass(AccountReactivatedEmailEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("inactive@test.local", eventCaptor.getValue().getRecipientEmail());
        assertEquals("Grace", eventCaptor.getValue().getFirstName());
    }

    @Test
    void reactivateUser_whenAlreadyActive_throwsBusinessRuleException() {
        User user = User.builder()
                .id(43L)
                .email("active@test.local")
                .passwordHash("x")
                .firstName("Active")
                .lastName("User")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();
        when(userRepository.findById(43L)).thenReturn(Optional.of(user));

        BusinessRuleException thrown = assertThrows(BusinessRuleException.class, () -> userService.reactivateUser(43L));
        assertNotNull(thrown);
    }
}
