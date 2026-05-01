package com.reviewflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.reviewflow.assignment.controller.AssignmentController;
import com.reviewflow.assignment.exception.SubmissionTypeLockedException;
import com.reviewflow.assignment.dto.request.CreateAssignmentRequest;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.assignment.service.AssignmentService;
import com.reviewflow.shared.util.HashidService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentControllerIntegrationTest {

  @Mock private AssignmentService assignmentService;

  @Mock private HashidService hashidService;

  private AssignmentController controller() {
    return new AssignmentController(assignmentService, hashidService);
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
  void updateAssignment_changeSubmissionTypeAfterCollaboration_throwsSubmissionTypeLocked() {
    when(hashidService.decodeOrThrow("ASSIGN4")).thenReturn(505L);
    when(assignmentService.updateAssignment(
            eq(505L),
            eq("Updated Title"),
            eq("Updated Desc"),
            any(),
            isNull(),
            eq(SubmissionType.TEAM),
            isNull(),
            eq(77L),
            isNull(),
            isNull(),
            isNull()))
        .thenThrow(
            new SubmissionTypeLockedException(
                "Assignment submission type cannot be changed after teams or submissions exist"));

    CreateAssignmentRequest request = new CreateAssignmentRequest();
    request.setTitle("Updated Title");
    request.setDescription("Updated Desc");
    request.setDueAt(Instant.parse("2027-01-01T00:00:00Z"));
    request.setSubmissionType(SubmissionType.TEAM);

    SubmissionTypeLockedException thrown =
        assertThrows(
            SubmissionTypeLockedException.class,
            () -> controller().update("ASSIGN4", request, instructorPrincipal()));
    assertEquals(
        "Assignment submission type cannot be changed after teams or submissions exist",
        thrown.getMessage());
  }
}
