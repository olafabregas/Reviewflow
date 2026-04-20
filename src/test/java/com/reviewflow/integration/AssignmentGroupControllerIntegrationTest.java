package com.reviewflow.integration;

import com.reviewflow.controller.AssignmentGroupController;
import com.reviewflow.exception.GroupNotInCourseException;
import com.reviewflow.exception.ValidationException;
import com.reviewflow.model.dto.request.CreateAssignmentGroupRequest;
import com.reviewflow.model.dto.request.MoveAssignmentGroupRequest;
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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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

        MoveAssignmentGroupRequest request = new MoveAssignmentGroupRequest();
        request.setGroupId("GROUP_HASH");

        GroupNotInCourseException thrown = assertThrows(GroupNotInCourseException.class,
            () -> controller().moveAssignment("ASSIGN_HASH", request, instructorPrincipal()));
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

        MoveAssignmentGroupRequest request = new MoveAssignmentGroupRequest();
        request.setGroupId("GROUP_HASH");

        var entity = controller().moveAssignment("ASSIGN_HASH", request, instructorPrincipal());

        assertEquals(200, entity.getStatusCode().value());
        assertEquals("Projects", entity.getBody().getData().getNewGroupName());
    }

    @Test
    void moveAssignment_missingGroupId_throwsInvalidRequest() {
        MoveAssignmentGroupRequest request = new MoveAssignmentGroupRequest();

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> controller().moveAssignment("ASSIGN_HASH", request, instructorPrincipal()));

        assertEquals("groupId is required", thrown.getMessage());
        assertEquals("INVALID_REQUEST", thrown.getCode());
    }

    @Test
    void moveAssignment_blankGroupId_throwsInvalidRequest() {
        MoveAssignmentGroupRequest request = new MoveAssignmentGroupRequest();
        request.setGroupId("   ");

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> controller().moveAssignment("ASSIGN_HASH", request, instructorPrincipal()));

        assertEquals("groupId is required", thrown.getMessage());
        assertEquals("INVALID_REQUEST", thrown.getCode());
    }

    @Test
    void list_validRequest_verifiesAuthorizationBeforeFetch() {
        when(hashidService.decodeOrThrow("COURSE_HASH")).thenReturn(10L);

        controller().list("COURSE_HASH", instructorPrincipal());

        verify(assignmentGroupService).verifyCanView(10L, 77L);
        verify(assignmentGroupService).listByCourse(10L);
    }

    @Test
    void list_nonEnrolledUser_propagatesForbidden() {
        when(hashidService.decodeOrThrow("COURSE_HASH")).thenReturn(10L);
        doThrow(new AccessDeniedException("Not authorized to view assignment groups for this course"))
                .when(assignmentGroupService).verifyCanView(10L, 77L);

        AccessDeniedException thrown = assertThrows(AccessDeniedException.class,
                () -> controller().list("COURSE_HASH", instructorPrincipal()));

        assertEquals("Not authorized to view assignment groups for this course", thrown.getMessage());
    }
}













