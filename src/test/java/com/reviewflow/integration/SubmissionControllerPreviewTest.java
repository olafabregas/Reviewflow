package com.reviewflow.integration;

import com.reviewflow.controller.SubmissionController;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.PreviewResponseDto;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.util.HashidService;
import com.reviewflow.service.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionControllerPreviewTest {

    private SubmissionController controller;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private HashidService hashidService;

    private ReviewFlowUserDetails mockUser;

    @BeforeEach
    void setUp() {
        controller = new SubmissionController(submissionService, hashidService);
        User user = User.builder()
                .id(100L)
                .email("student@example.com")
                .firstName("John")
                .lastName("Doe")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();
        mockUser = new ReviewFlowUserDetails(user);
    }

    @Test
    void preview_withValidSubmission_returns200WithPreviewDto() {
        // Arrange
        PreviewResponseDto previewDto = PreviewResponseDto.builder()
                .previewUrl("https://s3.amazonaws.com/presigned-url")
                .contentType("application/pdf")
                .expiresInSeconds(900L)
                .filename("document.pdf")
                .build();

        when(submissionService.getPreviewUrl("sub123", 100L, UserRole.STUDENT))
                .thenReturn(previewDto);

        // Act
        ResponseEntity<ApiResponse<PreviewResponseDto>> response = controller.preview("sub123", mockUser);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(previewDto, response.getBody().getData());
        verify(submissionService).getPreviewUrl("sub123", 100L, UserRole.STUDENT);
    }

    @Test
    void preview_previewUrlReturnsExpiredTime() {
        // Arrange
        PreviewResponseDto previewDto = PreviewResponseDto.builder()
                .previewUrl("https://s3.amazonaws.com/presigned?expiry=...")
                .contentType("image/png")
                .expiresInSeconds(900L)
                .filename("image.png")
                .build();

        when(submissionService.getPreviewUrl("sub456", 100L, UserRole.STUDENT))
                .thenReturn(previewDto);

        // Act
        ResponseEntity<ApiResponse<PreviewResponseDto>> response = controller.preview("sub456", mockUser);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        PreviewResponseDto responseData = response.getBody().getData();
        assertEquals("image/png", responseData.getContentType());
        assertEquals(900L, responseData.getExpiresInSeconds());
    }

    @Test
    void preview_multipleContentTypes_returnsCorrectMimeType() {
        // Test with different MIME types
        String[] mimeTypes = {"application/pdf", "image/jpeg", "image/png", "image/webp"};

        for (String mimeType : mimeTypes) {
            // Arrange
            PreviewResponseDto previewDto = PreviewResponseDto.builder()
                    .previewUrl("https://s3.amazonaws.com/presigned-url")
                    .contentType(mimeType)
                    .expiresInSeconds(900L)
                    .filename("file." + mimeType.split("/")[1])
                    .build();

            when(submissionService.getPreviewUrl(anyString(), eq(100L), eq(UserRole.STUDENT)))
                    .thenReturn(previewDto);

            // Act
            ResponseEntity<ApiResponse<PreviewResponseDto>> response = controller.preview("subId", mockUser);

            // Assert
            assertEquals(mimeType, response.getBody().getData().getContentType());
        }
    }

    @Test
    void preview_instructorAccess_returns200() {
        // Arrange
        User instructor = User.builder()
                .id(200L)
                .email("instructor@example.com")
                .firstName("Prof")
                .lastName("Smith")
                .role(UserRole.INSTRUCTOR)
                .isActive(true)
                .build();
        ReviewFlowUserDetails instructorUser = new ReviewFlowUserDetails(instructor);

        PreviewResponseDto previewDto = PreviewResponseDto.builder()
                .previewUrl("https://s3.amazonaws.com/presigned-url")
                .contentType("application/pdf")
                .expiresInSeconds(900L)
                .filename("document.pdf")
                .build();

        when(submissionService.getPreviewUrl("sub123", 200L, UserRole.INSTRUCTOR))
                .thenReturn(previewDto);

        // Act
        ResponseEntity<ApiResponse<PreviewResponseDto>> response = controller.preview("sub123", instructorUser);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(submissionService).getPreviewUrl("sub123", 200L, UserRole.INSTRUCTOR);
    }
}
