package com.reviewflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.reviewflow.grading.controller.InstructorScoreController;
import com.reviewflow.grading.dto.request.CreateInstructorScoreRequest;
import com.reviewflow.grading.dto.request.InstructorScoreImportCommitRequest;
import com.reviewflow.grading.dto.response.InstructorScoreImportCommitResponse;
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
  void commitImport_validRequest_returnsOk() {
    when(hashidService.decodeOrThrow("asgHash")).thenReturn(10L);

    InstructorScoreImportCommitResponse response =
        InstructorScoreImportCommitResponse.builder()
            .created(2)
            .updated(1)
            .message("3 scores saved as drafts")
            .build();

    when(csvImportService.commit(10L, 77L, "imp-1")).thenReturn(response);

    InstructorScoreImportCommitRequest request = new InstructorScoreImportCommitRequest();
    request.setImportId("imp-1");

    var entity = controller().commitImport("asgHash", request, instructorPrincipal());
    assertEquals(200, entity.getStatusCode().value());
    assertEquals(2, entity.getBody().getData().getCreated());
    assertEquals(1, entity.getBody().getData().getUpdated());
  }
}
