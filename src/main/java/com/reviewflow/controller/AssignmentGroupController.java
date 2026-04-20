package com.reviewflow.controller;

import com.reviewflow.model.dto.request.CreateAssignmentGroupRequest;
import com.reviewflow.model.dto.request.MoveAssignmentGroupRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.AssignmentGroupListResponse;
import com.reviewflow.model.dto.response.AssignmentGroupMoveResponse;
import com.reviewflow.model.dto.response.AssignmentGroupResponse;
import com.reviewflow.exception.ValidationException;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.AssignmentGroupService;
import com.reviewflow.util.HashidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Assignment Groups", description = "Manage grade categories within a course")
public class AssignmentGroupController {

    private final AssignmentGroupService assignmentGroupService;
    private final HashidService hashidService;

    @Operation(summary = "Create assignment group", description = "Create a grade group for a course.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Assignment group created successfully", content = @Content(schema = @Schema(implementation = AssignmentGroupResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
    })
    @PostMapping("/courses/{courseId}/assignment-groups")
    public ResponseEntity<ApiResponse<AssignmentGroupResponse>> create(
            @PathVariable String courseId,
            @Valid @RequestBody CreateAssignmentGroupRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        AssignmentGroupResponse response = assignmentGroupService.create(
                hashidService.decodeOrThrow(courseId),
                user.getUserId(),
                request.getName(),
                request.getWeight(),
                request.getDropLowestN(),
                request.getDisplayOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Operation(summary = "List assignment groups", description = "Return all grade groups for a course.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Assignment groups retrieved successfully", content = @Content(schema = @Schema(implementation = AssignmentGroupListResponse.class)))
    })
    @GetMapping("/courses/{courseId}/assignment-groups")
    public ResponseEntity<ApiResponse<AssignmentGroupListResponse>> list(
            @PathVariable String courseId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long decodedCourseId = hashidService.decodeOrThrow(courseId);
        assignmentGroupService.verifyCanView(decodedCourseId, user.getUserId());
        AssignmentGroupListResponse response = assignmentGroupService.listByCourse(decodedCourseId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Update assignment group", description = "Update a course grade group.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Assignment group updated successfully", content = @Content(schema = @Schema(implementation = AssignmentGroupResponse.class)))
    })
    @PutMapping("/assignment-groups/{id}")
    public ResponseEntity<ApiResponse<AssignmentGroupResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody CreateAssignmentGroupRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        AssignmentGroupResponse response = assignmentGroupService.update(
                hashidService.decodeOrThrow(id),
                user.getUserId(),
                request.getName(),
                request.getWeight(),
                request.getDropLowestN(),
                request.getDisplayOrder());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Delete assignment group", description = "Delete an empty grade group.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Assignment group deleted successfully", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @DeleteMapping("/assignment-groups/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        assignmentGroupService.delete(hashidService.decodeOrThrow(id), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Assignment group deleted")));
    }

    @Operation(summary = "Move assignment to group", description = "Move an assignment into a different grade group.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Assignment moved successfully", content = @Content(schema = @Schema(implementation = AssignmentGroupMoveResponse.class)))
    })
    @PatchMapping("/assignments/{id}/group")
    public ResponseEntity<ApiResponse<AssignmentGroupMoveResponse>> moveAssignment(
            @PathVariable String id,
            @RequestBody MoveAssignmentGroupRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        if (request == null || request.getGroupId() == null || request.getGroupId().isBlank()) {
            throw new ValidationException("groupId is required", "INVALID_REQUEST");
        }

        AssignmentGroupMoveResponse response = assignmentGroupService.moveAssignment(
                hashidService.decodeOrThrow(id),
                hashidService.decodeOrThrow(request.getGroupId()),
                user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
