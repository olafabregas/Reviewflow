package com.reviewflow.integration;

import com.reviewflow.controller.AssignmentGroupController;
import com.reviewflow.exception.GroupNotInCourseException;
import com.reviewflow.model.dto.request.CreateAssignmentGroupRequest;
import com.reviewflow.model.dto.response.AssignmentGroupMoveResponse;
import com.reviewflow.model.dto.response.AssignmentGroupResponse;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.AssignmentGroupService;
import com.reviewflow.service.HashidService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentGroupControllerIntegrationTest {

    @Mock
    private AssignmentGroupService assignmentGroupService;

    @Mock
    private HashidService hashidService;

    private AssignmentGroupController controller() {
        return new AssignmentGroupController(assignmentGroupService, hashidService);
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

        AssignmentGroupResponse response = AssignmentGroupResponse.builder()
                .id("GRP_HASH")
                .name("Projects")
                .weight(new BigDecimal("40.00"))
                .dropLowestN(0)
                .displayOrder(1)
                .isUncategorized(false)
                .assignmentCount(0L)
                .build();

        when(assignmentGroupService.create(eq(10L), eq(77L), eq("Projects"), eq(new BigDecimal("40.00")), eq(0), eq(1)))
                .thenReturn(response);

        CreateAssignmentGroupRequest request = new CreateAssignmentGroupRequest();
        request.setName("Projects");
        request.setWeight(new BigDecimal("40.00"));
        request.setDropLowestN(0);
        request.setDisplayOrder(1);

        var entity = controller().create("COURSE_HASH", request, instructorPrincipal());

        assertEquals(201, entity.getStatusCode().value());
        assertEquals(true, entity.getBody().isSuccess());
        assertEquals("GRP_HASH", entity.getBody().getData().getId());
    }

    @Test
    void moveAssignment_groupFromAnotherCourse_propagatesException() {
        when(hashidService.decodeOrThrow("ASSIGN_HASH")).thenReturn(100L);
        when(hashidService.decodeOrThrow("GROUP_HASH")).thenReturn(200L);
        when(assignmentGroupService.moveAssignment(100L, 200L, 77L))
                .thenThrow(new GroupNotInCourseException("Assignment group does not belong to this course"));

        GroupNotInCourseException thrown = assertThrows(GroupNotInCourseException.class,
                () -> controller().moveAssignment("ASSIGN_HASH", Map.of("groupId", "GROUP_HASH"), instructorPrincipal()));
        assertEquals("Assignment group does not belong to this course", thrown.getMessage());
    }

    @Test
    void moveAssignment_happyPath_returnsPayload() {
        when(hashidService.decodeOrThrow("ASSIGN_HASH")).thenReturn(100L);
        when(hashidService.decodeOrThrow("GROUP_HASH")).thenReturn(200L);

        AssignmentGroupMoveResponse moveResponse = AssignmentGroupMoveResponse.builder()
                .assignmentId("ASSIGN_HASH")
                .newGroupId("GROUP_HASH")
                .newGroupName("Projects")
                .build();

        when(assignmentGroupService.moveAssignment(100L, 200L, 77L)).thenReturn(moveResponse);

        var entity = controller().moveAssignment("ASSIGN_HASH", Map.of("groupId", "GROUP_HASH"), instructorPrincipal());

        assertEquals(200, entity.getStatusCode().value());
        assertEquals("Projects", entity.getBody().getData().getNewGroupName());
    }
}
