package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import com.reviewflow.exception.*;
import com.reviewflow.model.dto.response.PreviewResponseDto;
import com.reviewflow.model.entity.*;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.pdf.PdfGenerationService;
import com.reviewflow.repository.*;
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
class EvaluationServicePreviewTest {

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private RubricScoreRepository rubricScoreRepository;

    @Mock
    private RubricCriterionRepository rubricCriterionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PdfGenerationService pdfGenerationService;

    @Mock
    private HashidService hashidService;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private EvaluationService evaluationService;

    private Evaluation testEvaluation;
    private User student;
    private User instructor;
    private Assignment assignment;
    private Submission submission;

    @BeforeEach
    void setUp() {
        instructor = User.builder()
                .id(200L)
                .email("instructor@example.com")
                .firstName("Prof")
                .lastName("Smith")
                .build();

        student = User.builder()
                .id(100L)
                .email("student@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();

        Course course = Course.builder()
                .id(1L)
                .code("CS101")
                .build();

        assignment = Assignment.builder()
                .id(10L)
                .title("Assignment 1")
                .course(course)
                .submissionType(SubmissionType.INDIVIDUAL)
                .build();

        submission = Submission.builder()
                .id(1000L)
                .student(student)
                .assignment(assignment)
                .fileName("solution.pdf")
                .build();

        testEvaluation = Evaluation.builder()
                .id(500L)
                .submission(submission)
                .instructor(instructor)
                .pdfPath("pdfs/eval500/report.pdf")
                .isDraft(false)
                .build();
    }

    @Test
    void getPdfPreviewUrl_publishedEvaluation_success() {
        // Arrange
        when(hashidService.decode("eval123")).thenReturn(500L);
        when(evaluationRepository.findById(500L)).thenReturn(Optional.of(testEvaluation));
        when(s3Service.getObjectSize("pdfs/eval500/report.pdf")).thenReturn(2048L);
        when(s3Service.generatePresignedPreviewUrl("pdfs/eval500/report.pdf", "application/pdf"))
                .thenReturn("https://s3.amazonaws.com/presigned-pdf");

        // Act
        PreviewResponseDto result = evaluationService.getPdfPreviewUrl("eval123", 100L, UserRole.STUDENT);

        // Assert
        assertNotNull(result);
        assertEquals("https://s3.amazonaws.com/presigned-pdf", result.getPreviewUrl());
        assertEquals("application/pdf", result.getContentType());
        assertEquals(900L, result.getExpiresInSeconds());
        assertEquals("evaluation.pdf", result.getFilename());
    }

    @Test
    void getPdfPreviewUrl_studentDraftEvaluation_throws404() {
        // Arrange
        testEvaluation.setIsDraft(true);
        when(hashidService.decode("eval123")).thenReturn(500L);
        when(evaluationRepository.findById(500L)).thenReturn(Optional.of(testEvaluation));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> evaluationService.getPdfPreviewUrl("eval123", 100L, UserRole.STUDENT));
    }

    @Test
    void getPdfPreviewUrl_pdfNotGenerated_throws404() {
        // Arrange
        testEvaluation.setPdfPath(null);
        when(hashidService.decode("eval123")).thenReturn(500L);
        when(evaluationRepository.findById(500L)).thenReturn(Optional.of(testEvaluation));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> evaluationService.getPdfPreviewUrl("eval123", 100L, UserRole.STUDENT));
    }

    @Test
    void getPdfPreviewUrl_fileTooLarge_throws409() {
        // Arrange
        when(hashidService.decode("eval123")).thenReturn(500L);
        when(evaluationRepository.findById(500L)).thenReturn(Optional.of(testEvaluation));
        when(s3Service.getObjectSize("pdfs/eval500/report.pdf")).thenReturn(60_000_000L);

        // Act & Assert
        assertThrows(FileTooLargeForPreviewException.class,
                () -> evaluationService.getPdfPreviewUrl("eval123", 100L, UserRole.STUDENT));
    }

    @Test
    void getPdfPreviewUrl_invalidHashId_throws404() {
        // Arrange
        when(hashidService.decode("invalid")).thenThrow(new IllegalArgumentException("Invalid hashid"));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> evaluationService.getPdfPreviewUrl("invalid", 100L, UserRole.STUDENT));
    }

    @Test
    void getPdfPreviewUrl_studentWrongSubmission_throws403() {
        // Arrange
        testEvaluation.getSubmission().setStudent(User.builder().id(999L).build()); // Different student
        when(hashidService.decode("eval123")).thenReturn(500L);
        when(evaluationRepository.findById(500L)).thenReturn(Optional.of(testEvaluation));

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> evaluationService.getPdfPreviewUrl("eval123", 100L, UserRole.STUDENT));
    }

    @Test
    void getPdfPreviewUrl_instructorOwnEval_success() {
        // Arrange
        when(hashidService.decode("eval123")).thenReturn(500L);
        when(evaluationRepository.findById(500L)).thenReturn(Optional.of(testEvaluation));
        when(s3Service.getObjectSize("pdfs/eval500/report.pdf")).thenReturn(2048L);
        when(s3Service.generatePresignedPreviewUrl("pdfs/eval500/report.pdf", "application/pdf"))
                .thenReturn("https://s3.amazonaws.com/presigned-pdf");

        // Act
        PreviewResponseDto result = evaluationService.getPdfPreviewUrl("eval123", 200L, UserRole.INSTRUCTOR);

        // Assert
        assertNotNull(result);
        assertEquals("https://s3.amazonaws.com/presigned-pdf", result.getPreviewUrl());
    }

    @Test
    void getPdfPreviewUrl_instructorOtherEval_throws403() {
        // Arrange
        testEvaluation.setInstructor(User.builder().id(201L).build()); // Different instructor
        when(hashidService.decode("eval123")).thenReturn(500L);
        when(evaluationRepository.findById(500L)).thenReturn(Optional.of(testEvaluation));

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> evaluationService.getPdfPreviewUrl("eval123", 200L, UserRole.INSTRUCTOR));
    }

    @Test
    void getPdfPreviewUrl_adminCanAccessAny_success() {
        // Arrange
        Long adminId = 300L;
        when(hashidService.decode("eval123")).thenReturn(500L);
        when(evaluationRepository.findById(500L)).thenReturn(Optional.of(testEvaluation));
        when(s3Service.getObjectSize("pdfs/eval500/report.pdf")).thenReturn(2048L);
        when(s3Service.generatePresignedPreviewUrl("pdfs/eval500/report.pdf", "application/pdf"))
                .thenReturn("https://s3.amazonaws.com/presigned-pdf");

        // Act
        PreviewResponseDto result = evaluationService.getPdfPreviewUrl("eval123", adminId, UserRole.ADMIN);

        // Assert
        assertNotNull(result);
        assertEquals("https://s3.amazonaws.com/presigned-pdf", result.getPreviewUrl());
    }

    @Test
    void getPdfPreviewUrl_studentTeamSubmissionMember_success() {
        // Arrange
        Team team = Team.builder()
                .id(50L)
                .name("Team A")
                .build();

        submission.setStudent(null);
        submission.setTeam(team);
        assignment.setSubmissionType(SubmissionType.TEAM);

        when(hashidService.decode("eval123")).thenReturn(500L);
        when(evaluationRepository.findById(500L)).thenReturn(Optional.of(testEvaluation));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(50L, 100L)).thenReturn(Optional.of(
                TeamMember.builder().id(1L).build()
        ));
        when(s3Service.getObjectSize("pdfs/eval500/report.pdf")).thenReturn(2048L);
        when(s3Service.generatePresignedPreviewUrl("pdfs/eval500/report.pdf", "application/pdf"))
                .thenReturn("https://s3.amazonaws.com/presigned-pdf");

        // Act
        PreviewResponseDto result = evaluationService.getPdfPreviewUrl("eval123", 100L, UserRole.STUDENT);

        // Assert
        assertNotNull(result);
        assertEquals("https://s3.amazonaws.com/presigned-pdf", result.getPreviewUrl());
    }
}
