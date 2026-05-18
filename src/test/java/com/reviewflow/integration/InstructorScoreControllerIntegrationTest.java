package com.reviewflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.reviewflow.grading.controller.InstructorScoreController;
import com.reviewflow.grading.dto.request.CreateInstructorScoreRequest;
import com.reviewflow.grading.dto.response.ImportJobStartResponse;
import com.reviewflow.grading.job.JobStatus;
import com.reviewflow.grading.dto.response.InstructorScoreResponse;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.grading.service.CsvImportService;
import com.reviewflow.grading.service.InstructorScoreService;
import com.reviewflow.shared.util.HashidService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstructorScoreControllerIntegrationTest {

  @Mock private InstructorScoreService instructorScoreService;

  @Mock private CsvImportService csvImportService;

  @Mock private HashidService hashidService;

  private InstructorScoreController controller() {
    return new InstructorScoreController(instructorScoreService, csvImportService, hashidService);
  }

  private ReviewFlowUserDetails instructorPrincipal() {
    User user =
        User.builder()
            .id(77L)
            .email("instructor@test.local")
            .passwordHash("x")
            .role(UserRole.INSTRUCTOR)
            .isActive(true)
            .build();
    return new ReviewFlowUserDetails(user);
  }

  @Test
  void create_validRequest_returnsCreated() {
    when(hashidService.decodeOrThrow("asgHash")).thenReturn(10L);
    when(hashidService.decodeOrThrow("stuHash")).thenReturn(100L);

    InstructorScoreResponse response =
        InstructorScoreResponse.builder()
            .id("iscHash")
            .assignmentId("asgHash")
            .studentId("stuHash")
            .score(new BigDecimal("84.00"))
            .maxScore(new BigDecimal("100.00"))
            .isPublished(false)
            .build();

    when(instructorScoreService.create(
            eq(10L), eq(77L), eq(100L), eq(null), eq(new BigDecimal("84.00")), eq("Good")))
        .thenReturn(response);

    CreateInstructorScoreRequest request = new CreateInstructorScoreRequest();
    request.setStudentId("stuHash");
    request.setScore(new BigDecimal("84.00"));
    request.setComment("Good");

    var entity = controller().create("asgHash", request, instructorPrincipal());
    assertEquals(201, entity.getStatusCode().value());
    assertEquals("iscHash", entity.getBody().getData().getId());
  }

  @Test
  void startImport_validRequest_returnsAccepted() {
    when(hashidService.decodeOrThrow("asgHash")).thenReturn(10L);

    ImportJobStartResponse response =
        ImportJobStartResponse.builder().jobId("job-uuid").status(JobStatus.UPLOADED).build();

    when(csvImportService.startImport(eq(10L), eq(77L), org.mockito.ArgumentMatchers.any()))
        .thenReturn(response);

    var entity =
        controller()
            .startImport(
                "asgHash",
                new org.springframework.mock.web.MockMultipartFile(
                    "file", "scores.csv", "text/csv", "student_email,score\na@b.com,90".getBytes()),
                instructorPrincipal());
    assertEquals(202, entity.getStatusCode().value());
    assertEquals("job-uuid", entity.getBody().getData().getJobId());
    assertEquals(JobStatus.UPLOADED, entity.getBody().getData().getStatus());
  }
}
