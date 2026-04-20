package com.reviewflow.integration;

import com.reviewflow.controller.EvaluationController;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.PreviewResponseDto;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.repository.RubricScoreRepository;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.EvaluationService;
import com.reviewflow.util.HashidService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationControllerPreviewTest {

    private EvaluationController controller;

    @Mock
    private EvaluationService evaluationService;

    @Mock
    private HashidService hashidService;

    @Mock
    private RubricScoreRepository rubricScoreRepository;

    private ReviewFlowUserDetails mockStudent;
    private ReviewFlowUserDetails mockInstructor;

    @BeforeEach
    void setUp() {
        controller = new EvaluationController(evaluationService, rubricScoreRepository, hashidService);
        User studentUser = User.builder()
                .id(100L)
                .email("student@example.com")
                .firstName("John")
                .lastName("Doe")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();
        mockStudent = new ReviewFlowUserDetails(studentUser);

        User instructorUser = User.builder()
                .id(200L)
                .email("instructor@example.com")
                .firstName("Prof")
                .lastName("Smith")
                .role(UserRole.INSTRUCTOR)
                .isActive(true)
                .build();
        mockInstructor = new ReviewFlowUserDetails(instructorUser);
    }

    @Test
    void previewPdf_withValidEvaluation_returns200WithPreviewDto() {
        // Arrange
        PreviewResponseDto previewDto = PreviewResponseDto.builder()
                .previewUrl("https://s3.amazonaws.com/presigned-pdf-url")
                .contentType("application/pdf")
                .expiresInSeconds(900L)
                .filename("evaluation.pdf")
                .build();

        when(evaluationService.getPdfPreviewUrl("eval123", 100L, UserRole.STUDENT))
                .thenReturn(previewDto);

        // Act
        ResponseEntity<ApiResponse<PreviewResponseDto>> response = controller.previewPdf("eval123", mockStudent);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(previewDto, response.getBody().getData());
        assertEquals("application/pdf", response.getBody().getData().getContentType());
        verify(evaluationService).getPdfPreviewUrl("eval123", 100L, UserRole.STUDENT);
    }

    @Test
    void previewPdf_instructorAccess_returns200() {
        // Arrange
        PreviewResponseDto previewDto = PreviewResponseDto.builder()
                .previewUrl("https://s3.amazonaws.com/presigned-pdf-url")
                .contentType("application/pdf")
                .expiresInSeconds(900L)
                .filename("evaluation.pdf")
                .build();

        when(evaluationService.getPdfPreviewUrl("eval456", 200L, UserRole.INSTRUCTOR))
                .thenReturn(previewDto);

        // Act
        ResponseEntity<ApiResponse<PreviewResponseDto>> response = controller.previewPdf("eval456", mockInstructor);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/pdf", response.getBody().getData().getContentType());
        verify(evaluationService).getPdfPreviewUrl("eval456", 200L, UserRole.INSTRUCTOR);
    }

    @Test
    void previewPdf_returnsPdfContentType() {
        // Arrange
        PreviewResponseDto pdfPreview = PreviewResponseDto.builder()
                .previewUrl("https://s3.amazonaws.com/presigned-pdf")
                .contentType("application/pdf")
                .expiresInSeconds(900L)
                .filename("evaluation.pdf")
                .build();

        when(evaluationService.getPdfPreviewUrl(anyString(), eq(100L), eq(UserRole.STUDENT)))
                .thenReturn(pdfPreview);

        // Act
        ResponseEntity<ApiResponse<PreviewResponseDto>> response = controller.previewPdf("evalId", mockStudent);

        // Assert
        PreviewResponseDto data = response.getBody().getData();
        assertEquals("application/pdf", data.getContentType());
        assertTrue(data.getPreviewUrl().contains("presigned"));
        assertEquals(900L, data.getExpiresInSeconds());
    }
}
