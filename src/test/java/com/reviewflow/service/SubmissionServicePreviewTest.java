package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import com.reviewflow.exception.*;
import com.reviewflow.model.dto.response.PreviewResponseDto;
import com.reviewflow.model.entity.*;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.*;
import com.reviewflow.storage.StorageService;
import com.reviewflow.monitoring.SecurityMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServicePreviewTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private HashidService hashidService;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private CourseInstructorRepository courseInstructorRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private ExtensionRequestRepository extensionRequestRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileSecurityValidator fileSecurityValidator;

    @Mock
    private AdminStatsService adminStatsService;

    @Mock
    private ClamAvScanService clamAvScanService;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private SecurityMetrics securityMetrics;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private SubmissionService submissionService;

    private Submission testSubmission;
    private User student;
    private Assignment assignment;
    private Course course;

    @BeforeEach
    void setUp() {
        // Create test data
        course = Course.builder()
                .id(1L)
                .code("CS101")
                .build();

        assignment = Assignment.builder()
                .id(10L)
                .title("Assignment 1")
                .course(course)
                .submissionType(SubmissionType.INDIVIDUAL)
                .build();

        student = User.builder()
                .id(100L)
                .email("student@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();

        testSubmission = Submission.builder()
                .id(1000L)
                .student(student)
                .assignment(assignment)
                .fileName("report.pdf")
                .fileSizeBytes(1024L)
                .versionNumber(1)
                .uploadedAt(Instant.now())
                .uploadedBy(student)
                .build();
    }

    @Test
    void getPreviewUrl_studentOwner_previewablePdf_success() {
        // Arrange
        when(hashidService.decode("sub123")).thenReturn(1000L);
        when(submissionRepository.findById(1000L)).thenReturn(Optional.of(testSubmission));
        when(hashidService.encode(10L)).thenReturn("assign10");
        when(hashidService.encode(100L)).thenReturn("user100");
        when(s3Service.getObjectSize(anyString())).thenReturn(1024L);
        when(s3Service.generatePresignedPreviewUrl(anyString(), eq("application/pdf")))
                .thenReturn("https://s3.amazonaws.com/presigned-url");

        // Act
        PreviewResponseDto result = submissionService.getPreviewUrl("sub123", 100L, UserRole.STUDENT);

        // Assert
        assertNotNull(result);
        assertEquals("https://s3.amazonaws.com/presigned-url", result.getPreviewUrl());
        assertEquals("application/pdf", result.getContentType());
        assertEquals(900L, result.getExpiresInSeconds()); // 15 * 60 = 900
        assertEquals("report.pdf", result.getFilename());
        verify(s3Service).getObjectSize(anyString());
        verify(s3Service).generatePresignedPreviewUrl(anyString(), eq("application/pdf"));
    }

    @Test
    void getPreviewUrl_unsupportedFileType_throws409() {
        // Arrange
        testSubmission.setFileName("archive.zip");
        when(hashidService.decode("sub123")).thenReturn(1000L);
        when(submissionRepository.findById(1000L)).thenReturn(Optional.of(testSubmission));
        when(hashidService.encode(10L)).thenReturn("assign10");
        when(hashidService.encode(100L)).thenReturn("user100");
        when(s3Service.getObjectSize(anyString())).thenReturn(1024L);

        // Act & Assert
        assertThrows(PreviewNotSupportedException.class,
                () -> submissionService.getPreviewUrl("sub123", 100L, UserRole.STUDENT));
    }

    @Test
    void getPreviewUrl_fileTooLarge_throws409() {
        // Arrange
        when(hashidService.decode("sub123")).thenReturn(1000L);
        when(submissionRepository.findById(1000L)).thenReturn(Optional.of(testSubmission));
        when(hashidService.encode(10L)).thenReturn("assign10");
        when(hashidService.encode(100L)).thenReturn("user100");
        // 60 MB (exceeds 50 MB limit)
        when(s3Service.getObjectSize(anyString())).thenReturn(60_000_000L);

        // Act & Assert
        assertThrows(FileTooLargeForPreviewException.class,
                () -> submissionService.getPreviewUrl("sub123", 100L, UserRole.STUDENT));
    }

    @Test
    void getPreviewUrl_invalidHashId_throws404() {
        // Arrange
        when(hashidService.decode("invalid")).thenThrow(new IllegalArgumentException("Invalid hashid"));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> submissionService.getPreviewUrl("invalid", 100L, UserRole.STUDENT));
    }

    @Test
    void getPreviewUrl_studentNonOwner_throws403() {
        // Arrange
        testSubmission.setStudent(User.builder().id(999L).build()); // Different student
        when(hashidService.decode("sub123")).thenReturn(1000L);
        when(submissionRepository.findById(1000L)).thenReturn(Optional.of(testSubmission));

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> submissionService.getPreviewUrl("sub123", 100L, UserRole.STUDENT));
    }

    @Test
    void getPreviewUrl_instructorCourseOwner_success() {
        // Arrange
        Long instructorId = 200L;
        when(hashidService.decode("sub123")).thenReturn(1000L);
        when(submissionRepository.findById(1000L)).thenReturn(Optional.of(testSubmission));
        when(hashidService.encode(10L)).thenReturn("assign10");
        when(hashidService.encode(100L)).thenReturn("user100");
        when(s3Service.getObjectSize(anyString())).thenReturn(1024L);
        when(s3Service.generatePresignedPreviewUrl(anyString(), eq("application/pdf")))
                .thenReturn("https://s3.amazonaws.com/presigned-url");

        // Act
        PreviewResponseDto result = submissionService.getPreviewUrl("sub123", instructorId, UserRole.INSTRUCTOR);

        // Assert
        assertNotNull(result);
        assertEquals("https://s3.amazonaws.com/presigned-url", result.getPreviewUrl());
    }

    @Test
    void getPreviewUrl_adminCanAccessAny_success() {
        // Arrange
        Long adminId = 300L;
        when(hashidService.decode("sub123")).thenReturn(1000L);
        when(submissionRepository.findById(1000L)).thenReturn(Optional.of(testSubmission));
        when(hashidService.encode(10L)).thenReturn("assign10");
        when(hashidService.encode(100L)).thenReturn("user100");
        when(s3Service.getObjectSize(anyString())).thenReturn(1024L);
        when(s3Service.generatePresignedPreviewUrl(anyString(), eq("application/pdf")))
                .thenReturn("https://s3.amazonaws.com/presigned-url");

        // Act
        PreviewResponseDto result = submissionService.getPreviewUrl("sub123", adminId, UserRole.ADMIN);

        // Assert
        assertNotNull(result);
        assertEquals("https://s3.amazonaws.com/presigned-url", result.getPreviewUrl());
    }

    @Test
    void getPreviewUrl_previewableImageTypes_success() {
        // Arrange
        testSubmission.setFileName("image.png");
        when(hashidService.decode("sub123")).thenReturn(1000L);
        when(submissionRepository.findById(1000L)).thenReturn(Optional.of(testSubmission));
        when(hashidService.encode(10L)).thenReturn("assign10");
        when(hashidService.encode(100L)).thenReturn("user100");
        when(s3Service.getObjectSize(anyString())).thenReturn(2048L);
        when(s3Service.generatePresignedPreviewUrl(anyString(), eq("image/png")))
                .thenReturn("https://s3.amazonaws.com/presigned-image");

        // Act
        PreviewResponseDto result = submissionService.getPreviewUrl("sub123", 100L, UserRole.STUDENT);

        // Assert
        assertNotNull(result);
        assertEquals("image/png", result.getContentType());
        verify(s3Service).generatePresignedPreviewUrl(anyString(), eq("image/png"));
    }

    @Test
    void getPreviewUrl_teamSubmissionStudent_success() {
        // Arrange
        Team team = Team.builder()
                .id(50L)
                .name("Team A")
                .assignment(assignment)
                .build();

        testSubmission.setStudent(null);
        testSubmission.setTeam(team);
        testSubmission.setAssignment(assignment);
        assignment.setSubmissionType(SubmissionType.TEAM);

        when(hashidService.decode("sub123")).thenReturn(1000L);
        when(submissionRepository.findById(1000L)).thenReturn(Optional.of(testSubmission));
        when(hashidService.encode(10L)).thenReturn("assign10");
        when(hashidService.encode(50L)).thenReturn("team50");
        when(teamMemberRepository.findByTeam_IdAndUser_Id(50L, 100L)).thenReturn(Optional.of(
                TeamMember.builder().id(1L).user(student).team(team).build()
        ));
        when(s3Service.getObjectSize(anyString())).thenReturn(1024L);
        when(s3Service.generatePresignedPreviewUrl(anyString(), eq("application/pdf")))
                .thenReturn("https://s3.amazonaws.com/presigned-url");

        // Act
        PreviewResponseDto result = submissionService.getPreviewUrl("sub123", 100L, UserRole.STUDENT);

        // Assert
        assertNotNull(result);
        assertEquals("https://s3.amazonaws.com/presigned-url", result.getPreviewUrl());
    }

    @Test
    void getPreviewUrl_teamSubmissionNonMember_throws403() {
        // Arrange
        Team team = Team.builder()
                .id(50L)
                .name("Team A")
                .assignment(assignment)
                .build();

        testSubmission.setStudent(null);
        testSubmission.setTeam(team);
        assignment.setSubmissionType(SubmissionType.TEAM);

        when(hashidService.decode("sub123")).thenReturn(1000L);
        when(submissionRepository.findById(1000L)).thenReturn(Optional.of(testSubmission));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(50L, 100L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> submissionService.getPreviewUrl("sub123", 100L, UserRole.STUDENT));
    }
}
