package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.reviewflow.exception.AvatarInvalidTypeException;
import com.reviewflow.exception.AvatarNotFoundException;
import com.reviewflow.exception.AvatarUploadFailedException;
import com.reviewflow.model.dto.response.AuthUserResponse;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.repository.UserRepository;
import com.reviewflow.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class UserServiceAvatarTest {

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

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "avatarMaxSizeBytes", 2097152L);
        ReflectionTestUtils.setField(userService, "avatarAllowedExtensions", "jpg,jpeg,png,webp");
        ReflectionTestUtils.setField(userService, "avatarAllowedMimeTypes", "image/jpeg,image/png,image/webp");
        ReflectionTestUtils.setField(userService, "s3Bucket", "reviewflow-bucket");
        ReflectionTestUtils.setField(userService, "s3Region", "us-east-1");
        ReflectionTestUtils.setField(userService, "s3PublicBaseUrl", "");
    }

    @Test
    void uploadAvatar_happyPath_updatesUserAndAudits() throws Exception {
        Long userId = 10L;
        User user = User.builder()
                .id(userId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", "img".getBytes());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(hashidService.encode(userId)).thenReturn("U10HASH");
        when(exifStripperService.strip(file, "jpeg")).thenReturn("clean".getBytes());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthUserResponse response = userService.uploadAvatar(userId, file, "127.0.0.1");

        assertNotNull(response.getAvatarUrl());
        assertEquals("U10HASH", response.getUserId());
        verify(storageService).store(eq("avatars/U10HASH/avatar.jpg"), any(ByteArrayInputStream.class), eq(5L), eq("image/jpeg"));
        verify(auditService).log(eq(userId), eq("AVATAR_UPLOADED"), eq("User"), eq(userId), anyString(), eq("127.0.0.1"));
    }

    @Test
    void uploadAvatar_blankRegion_usesRegionlessBucketUrl() throws Exception {
        ReflectionTestUtils.setField(userService, "s3Region", "");

        Long userId = 18L;
        User user = User.builder()
                .id(userId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", "img".getBytes());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(hashidService.encode(userId)).thenReturn("U18HASH");
        when(exifStripperService.strip(file, "jpeg")).thenReturn("clean".getBytes());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthUserResponse response = userService.uploadAvatar(userId, file, "127.0.0.1");

        assertNotNull(response.getAvatarUrl());
        assertTrue(response.getAvatarUrl().startsWith("https://reviewflow-bucket.s3.amazonaws.com/avatars/U18HASH/avatar.jpg?v="));
    }

    @Test
    void uploadAvatar_invalidType_bubblesValidationException() throws Exception {
        Long userId = 11L;
        User user = User.builder()
                .id(userId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "avatar.exe", "application/octet-stream", "bin".getBytes());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new AvatarInvalidTypeException("bad type")).when(fileSecurityValidator).validateWithConfig(eq(file), any(ValidationConfig.class));

        assertThrows(AvatarInvalidTypeException.class, () -> userService.uploadAvatar(userId, file, "127.0.0.1"));
        verify(storageService, never()).store(anyString(), any(ByteArrayInputStream.class), anyLong(), anyString());
    }

    @Test
    void uploadAvatar_storageFailure_throwsUploadFailed() throws Exception {
        Long userId = 12L;
        User user = User.builder()
                .id(userId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "img".getBytes());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(hashidService.encode(userId)).thenReturn("U12HASH");
        when(exifStripperService.strip(file, "png")).thenReturn("clean".getBytes());
        doThrow(new RuntimeException("s3 down")).when(storageService)
                .store(eq("avatars/U12HASH/avatar.png"), any(ByteArrayInputStream.class), eq(5L), eq("image/png"));

        assertThrows(AvatarUploadFailedException.class, () -> userService.uploadAvatar(userId, file, "127.0.0.1"));
    }

    @Test
    void deleteAvatar_whenMissing_throwsNotFound() {
        Long userId = 13L;
        User user = User.builder()
                .id(userId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .avatarUrl(null)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(AvatarNotFoundException.class, () -> userService.deleteAvatar(userId, "127.0.0.1"));
    }

    @Test
    void deleteAvatar_whenBlankAvatarUrl_throwsNotFound() {
        Long userId = 131L;
        User user = User.builder()
                .id(userId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .avatarUrl("   ")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(AvatarNotFoundException.class, () -> userService.deleteAvatar(userId, "127.0.0.1"));
    }

    @Test
    void deleteAvatar_happyPath_dbNullAndBestEffortDelete() {
        Long userId = 14L;
        User user = User.builder()
                .id(userId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .avatarUrl("https://bucket.s3.us-east-1.amazonaws.com/avatars/U14/avatar.jpg?v=123")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(hashidService.encode(userId)).thenReturn("U14");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthUserResponse response = userService.deleteAvatar(userId, "127.0.0.1");

        assertEquals(null, response.getAvatarUrl());
        verify(storageService).delete("avatars/U14/avatar.jpg");
        verify(auditService).log(eq(userId), eq("AVATAR_DELETED"), eq("User"), eq(userId), anyString(), eq("127.0.0.1"));
    }

    @Test
    void deleteAvatar_storageDeleteFails_stillReturnsSuccess() {
        Long userId = 141L;
        User user = User.builder()
                .id(userId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .avatarUrl("https://bucket.s3.us-east-1.amazonaws.com/avatars/U141/avatar.jpg?v=123")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(hashidService.encode(userId)).thenReturn("U141");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("network issue")).when(storageService).delete("avatars/U141/avatar.jpg");

        AuthUserResponse response = userService.deleteAvatar(userId, "127.0.0.1");

        assertEquals(null, response.getAvatarUrl());
        verify(auditService).log(eq(userId), eq("AVATAR_DELETED"), eq("User"), eq(userId), anyString(), eq("127.0.0.1"));
    }

    @Test
    void adminDeleteAvatar_happyPath_returnsSuccess() {
        Long targetUserId = 16L;
        Long adminId = 100L;
        User user = User.builder()
                .id(targetUserId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .avatarUrl("https://bucket.s3.us-east-1.amazonaws.com/avatars/U16/avatar.png?v=123")
                .updatedAt(Instant.now())
                .build();

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(hashidService.encode(targetUserId)).thenReturn("U16");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthUserResponse response = userService.adminDeleteAvatar(targetUserId, adminId, "127.0.0.1");

        assertEquals(null, response.getAvatarUrl());
        verify(storageService).delete("avatars/U16/avatar.png");
        verify(auditService).log(eq(adminId), eq("ADMIN_AVATAR_REMOVED"), eq("User"), eq(targetUserId), anyString(), eq("127.0.0.1"));
    }

    @Test
    void adminDeleteAvatar_whenMissing_throwsNotFound() {
        Long targetUserId = 17L;
        Long adminId = 101L;
        User user = User.builder()
                .id(targetUserId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .avatarUrl(null)
                .build();

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));

        assertThrows(AvatarNotFoundException.class,
                () -> userService.adminDeleteAvatar(targetUserId, adminId, "127.0.0.1"));
    }

    @Test
    void adminDeleteAvatar_whenBlankAvatarUrl_throwsNotFound() {
        Long targetUserId = 171L;
        Long adminId = 102L;
        User user = User.builder()
                .id(targetUserId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .avatarUrl(" ")
                .build();

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));

        assertThrows(AvatarNotFoundException.class,
                () -> userService.adminDeleteAvatar(targetUserId, adminId, "127.0.0.1"));
    }

    @Test
    void adminDeleteAvatar_storageDeleteFails_stillReturnsSuccess() {
        Long targetUserId = 15L;
        Long adminId = 99L;
        User user = User.builder()
                .id(targetUserId)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .avatarUrl("https://bucket.s3.us-east-1.amazonaws.com/avatars/U15/avatar.webp?v=123")
                .updatedAt(Instant.now())
                .build();

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(hashidService.encode(targetUserId)).thenReturn("U15");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("network issue")).when(storageService).delete("avatars/U15/avatar.webp");

        AuthUserResponse response = userService.adminDeleteAvatar(targetUserId, adminId, "127.0.0.1");

        assertEquals(null, response.getAvatarUrl());
        verify(auditService).log(eq(adminId), eq("ADMIN_AVATAR_REMOVED"), eq("User"), eq(targetUserId), anyString(), eq("127.0.0.1"));
    }
}
