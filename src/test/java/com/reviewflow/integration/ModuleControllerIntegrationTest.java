package com.reviewflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.reviewflow.assignment.controller.ModuleController;
import com.reviewflow.assignment.dto.request.AssignModuleRequest;
import com.reviewflow.assignment.dto.request.CreateAssignmentModuleRequest;
import com.reviewflow.assignment.dto.response.AssignmentModuleMoveResponse;
import com.reviewflow.assignment.dto.response.AssignmentModuleResponse;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.assignment.service.ModuleService;
import com.reviewflow.shared.util.HashidService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModuleControllerIntegrationTest {

  @Mock private ModuleService moduleService;

  @Mock private HashidService hashidService;

  private ModuleController controller() {
    return new ModuleController(moduleService, hashidService);
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
  void create_validRequest_returnsCreatedResponse() {
    when(hashidService.decodeOrThrow("COURSE_HASH")).thenReturn(10L);

    AssignmentModuleResponse response =
        AssignmentModuleResponse.builder()
            .id("mod123")
            .name("Week 3")
            .displayOrder(3)
            .assignments(List.of())
            .build();

    when(moduleService.create(eq(10L), eq(77L), eq("Week 3"), eq(3))).thenReturn(response);

    CreateAssignmentModuleRequest request = new CreateAssignmentModuleRequest();
    request.setName("Week 3");
    request.setDisplayOrder(3);

    var entity = controller().create("COURSE_HASH", request, instructorPrincipal());

    assertEquals(201, entity.getStatusCode().value());
    assertEquals(true, entity.getBody().isSuccess());
    assertEquals("mod123", entity.getBody().getData().getId());
  }

  @Test
  void assignAssignmentToModule_nullModuleId_unmodulesAssignment() {
    when(hashidService.decodeOrThrow("ASSIGN_HASH")).thenReturn(100L);

    AssignmentModuleMoveResponse moveResponse =
        AssignmentModuleMoveResponse.builder()
            .assignmentId("ASSIGN_HASH")
            .moduleId(null)
            .moduleName(null)
            .build();

    when(moduleService.assignToModule(100L, null, 77L)).thenReturn(moveResponse);

    AssignModuleRequest request = new AssignModuleRequest();
    request.setModuleId(null);

    var entity =
        controller().assignAssignmentToModule("ASSIGN_HASH", request, instructorPrincipal());

    assertEquals(200, entity.getStatusCode().value());
    assertEquals("ASSIGN_HASH", entity.getBody().getData().getAssignmentId());
    assertEquals(null, entity.getBody().getData().getModuleId());
  }
}
