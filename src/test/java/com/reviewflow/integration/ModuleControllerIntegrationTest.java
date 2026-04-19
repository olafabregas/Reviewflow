package com.reviewflow.integration;

import com.reviewflow.controller.ModuleController;
import com.reviewflow.model.dto.request.AssignModuleRequest;
import com.reviewflow.model.dto.request.CreateAssignmentModuleRequest;
import com.reviewflow.model.dto.response.AssignmentModuleMoveResponse;
import com.reviewflow.model.dto.response.AssignmentModuleResponse;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.HashidService;
import com.reviewflow.service.ModuleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleControllerIntegrationTest {

    @Mock
    private ModuleService moduleService;

    @Mock
    private HashidService hashidService;

    private ModuleController controller() {
        return new ModuleController(moduleService, hashidService);
    }

    private ReviewFlowUserDetails instructorPrincipal() {
        User user = User.builder()
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

        AssignmentModuleResponse response = AssignmentModuleResponse.builder()
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

        AssignmentModuleMoveResponse moveResponse = AssignmentModuleMoveResponse.builder()
                .assignmentId("ASSIGN_HASH")
                .moduleId(null)
                .moduleName(null)
                .build();

        when(moduleService.assignToModule(100L, null, 77L)).thenReturn(moveResponse);

        AssignModuleRequest request = new AssignModuleRequest();
        request.setModuleId(null);

        var entity = controller().assignAssignmentToModule("ASSIGN_HASH", request, instructorPrincipal());

        assertEquals(200, entity.getStatusCode().value());
        assertEquals("ASSIGN_HASH", entity.getBody().getData().getAssignmentId());
        assertEquals(null, entity.getBody().getData().getModuleId());
    }
}
