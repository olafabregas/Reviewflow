package com.reviewflow.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reviewflow.model.dto.request.AddRubricRequest;
import com.reviewflow.model.dto.request.CreateAssignmentRequest;
import com.reviewflow.model.dto.request.UpdateRubricRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.AssignmentResponse;
import com.reviewflow.model.dto.response.GradebookEntryResponse;
import com.reviewflow.model.dto.response.SubmissionResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.RubricCriterion;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.AssignmentService;
import com.reviewflow.util.HashidService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(
        name = "Assignments",
        description = "Assignment management endpoints. Supports creating, publishing, and managing assignments "
        + "with rubric criteria for grading. Includes submission and gradebook views for instructors."
)
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final HashidService hashidService;

    @Operation(
            summary = "List assignments for course",
            description = "Retrieve all assignments in a course. Token-based access control: instructors and admins see all, "
            + "students see only published assignments for courses they're enrolled in."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Assignments retrieved successfully",
                content = @Content(schema = @Schema(implementation = List.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - no access to this course",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - course does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/courses/{courseId}/assignments")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> listByCourse(
            @PathVariable String courseId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long courseIdLong = hashidService.decodeOrThrow(courseId);
        List<Assignment> list = assignmentService.listAssignmentsForCourse(courseIdLong, user.getUserId(), user.getRole());
        List<AssignmentResponse> data = list.stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
            summary = "Create assignment",
            description = "Create new assignment in a course. Requires INSTRUCTOR role. Initializes with title, description, "
            + "due date, submission type, and optional team settings and rubric."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Assignment created successfully",
                content = @Content(schema = @Schema(implementation = AssignmentResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid assignment data",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - INSTRUCTOR role required for course",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PostMapping("/courses/{courseId}/assignments")
    public ResponseEntity<ApiResponse<AssignmentResponse>> create(
            @PathVariable String courseId,
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long courseIdLong = hashidService.decodeOrThrow(courseId);
        Long groupId = request.getGroupId() != null ? hashidService.decodeOrThrow(request.getGroupId()) : null;
        Long moduleId = request.getModuleId() != null ? hashidService.decodeOrThrow(request.getModuleId()) : null;
        Assignment a = assignmentService.createAssignment(
                courseIdLong, request.getTitle(), request.getDescription(), request.getDueAt(),
                request.getMaxTeamSize(), request.getSubmissionType(), request.getTeamLockAt(),
                request.getIsPublished(), user.getUserId(), groupId, moduleId, request.getMaxScore());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toResponse(a)));
    }

    @Operation(
            summary = "Get assignment details",
            description = "Retrieve specific assignment by ID with all details including rubric criteria. "
            + "Students can only access published assignments in courses they're enrolled in."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Assignment details retrieved",
                content = @Content(schema = @Schema(implementation = AssignmentResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - no access to this assignment",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - assignment does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<AssignmentResponse>> get(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        Assignment a = assignmentService.getAssignmentByIdWithAccessControl(assignmentId, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(a)));
    }

    @Operation(
            summary = "Update assignment",
            description = "Update assignment details (title, description, due date, etc). Requires INSTRUCTOR role for the course. "
            + "Cannot update published assignments."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Assignment updated successfully",
                content = @Content(schema = @Schema(implementation = AssignmentResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid update data",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - INSTRUCTOR role required or assignment already published",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - assignment does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PutMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<AssignmentResponse>> update(
            @PathVariable String id,
            @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        Long groupId = request.getGroupId() != null ? hashidService.decodeOrThrow(request.getGroupId()) : null;
        Long moduleId = request.getModuleId() != null ? hashidService.decodeOrThrow(request.getModuleId()) : null;
        Assignment a = assignmentService.updateAssignment(
                assignmentId, request.getTitle(), request.getDescription(), request.getDueAt(),
                request.getMaxTeamSize(), request.getSubmissionType(), request.getTeamLockAt(), user.getUserId(), groupId, moduleId, request.getMaxScore());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(a)));
    }

    @Operation(
            summary = "Publish assignment",
            description = "Make assignment visible to students. Once published, assignment details are locked for updates. "
            + "Students will receive notifications. Requires INSTRUCTOR role."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Assignment published successfully",
                content = @Content(schema = @Schema(implementation = AssignmentResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - INSTRUCTOR role required or already published",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - assignment does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/assignments/{id}/publish")
    public ResponseEntity<ApiResponse<AssignmentResponse>> publish(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        Assignment a = assignmentService.publishAssignment(assignmentId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(a)));
    }

    @Operation(
            summary = "Get my assignments",
            description = "Retrieve authenticated user's assignments (both instructor-created and student-enrolled). "
            + "Supports filtering by status (UPCOMING, PAST_DUE, ALL) and course. Instructors see all their courses' assignments."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Assignments retrieved successfully",
                content = @Content(schema = @Schema(implementation = List.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/assignments")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> getMyAssignments(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            @RequestParam(required = false) String status, // UPCOMING|PAST_DUE|ALL
            @RequestParam(required = false) String courseId) {
        Long courseIdLong = courseId != null ? hashidService.decodeOrThrow(courseId) : null;
        List<AssignmentResponse> data = assignmentService.listAssignmentsForUserWithDetails(
                user.getUserId(), user.getRole(), status, courseIdLong);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
            summary = "Delete assignment",
            description = "Permanently delete an assignment. Requires INSTRUCTOR role for the course. "
            + "Deletion cascades to submissions and evaluations."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Assignment deleted successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - INSTRUCTOR role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - assignment does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        assignmentService.deleteAssignment(assignmentId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Assignment deleted")));
    }

    @Operation(
            summary = "Add rubric criterion",
            description = "Add a single grading criterion to assignment rubric. Required fields: name, maxScore, displayOrder. "
            + "Requires INSTRUCTOR role. Used to build rubric scoring matrix."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Rubric criterion created successfully",
                content = @Content(schema = @Schema(implementation = AssignmentResponse.RubricCriterionResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - missing required fields (name, maxScore, displayOrder)",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - INSTRUCTOR role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PostMapping("/assignments/{id}/rubric")
    public ResponseEntity<ApiResponse<AssignmentResponse.RubricCriterionResponse>> addRubric(
            @PathVariable String id,
            @Valid @RequestBody AddRubricRequest body,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        RubricCriterion c = assignmentService.addRubricCriteria(
                assignmentId,
                body.getName(),
                body.getDescription(),
                body.getMaxScore(),
                body.getDisplayOrder() != null ? body.getDisplayOrder() : 0,
                user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toCriterionResponse(c)));
    }

    @Operation(
            summary = "Update rubric criterion",
            description = "Update an existing rubric criterion. Optional fields: name, description, maxScore, displayOrder. "
            + "Requires INSTRUCTOR role. Changes do not affect already-completed evaluations."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Rubric criterion updated successfully",
                content = @Content(schema = @Schema(implementation = AssignmentResponse.RubricCriterionResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid criterion data",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - INSTRUCTOR role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - criterion does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PutMapping("/assignments/{id}/rubric/{criterionId}")
    public ResponseEntity<ApiResponse<AssignmentResponse.RubricCriterionResponse>> updateRubric(
            @PathVariable String id,
            @PathVariable String criterionId,
            @RequestBody UpdateRubricRequest body,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        Long criterionIdLong = hashidService.decodeOrThrow(criterionId);
        RubricCriterion c = assignmentService.updateRubricCriteria(
                assignmentId, criterionIdLong,
                body.getName(), body.getDescription(),
                body.getMaxScore(), body.getDisplayOrder(),
                user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toCriterionResponse(c)));
    }

    @Operation(
            summary = "Delete rubric criterion",
            description = "Remove a grading criterion from assignment rubric. Requires INSTRUCTOR role. "
            + "Cannot delete if already used in evaluations."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Rubric criterion deleted successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - INSTRUCTOR role required or criterion in use",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - criterion does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @DeleteMapping("/assignments/{id}/rubric/{criterionId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteRubric(
            @PathVariable String id,
            @PathVariable String criterionId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        Long criterionIdLong = hashidService.decodeOrThrow(criterionId);
        assignmentService.deleteRubricCriterion(assignmentId, criterionIdLong, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Criterion deleted")));
    }

    @Operation(
            summary = "Get assignment submissions",
            description = "Retrieve all submissions for an assignment including version history. "
            + "Requires INSTRUCTOR role. Used to view and grade student work."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Submissions retrieved successfully",
                content = @Content(schema = @Schema(implementation = List.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - INSTRUCTOR role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - assignment does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @GetMapping("/assignments/{id}/submissions")
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> getSubmissions(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        List<Submission> submissions = assignmentService.getSubmissionsForAssignment(assignmentId, user.getUserId());
        List<SubmissionResponse> data = submissions.stream().map(this::toSubmissionResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
            summary = "Get assignment gradebook",
            description = "Retrieve grading summary for all students on an assignment. Shows scores, submission status, "
            + "and evaluation status. Requires INSTRUCTOR role."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Gradebook retrieved successfully",
                content = @Content(schema = @Schema(implementation = List.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - INSTRUCTOR role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - assignment does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @GetMapping("/assignments/{id}/gradebook")
    public ResponseEntity<ApiResponse<List<GradebookEntryResponse>>> getGradebook(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        List<GradebookEntryResponse> data = assignmentService.getGradebookForAssignment(assignmentId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private SubmissionResponse toSubmissionResponse(Submission s) {
        return SubmissionResponse.from(s, hashidService);
    }

    private AssignmentResponse toResponse(Assignment a) {
        List<AssignmentResponse.RubricCriterionResponse> criteria = a.getRubricCriteria() != null
                ? a.getRubricCriteria().stream().map(this::toCriterionResponse).collect(Collectors.toList())
                : List.of();
        return AssignmentResponse.builder()
                .id(hashidService.encode(a.getId()))
                .courseId(hashidService.encode(a.getCourse() != null ? a.getCourse().getId() : null))
                .courseCode(a.getCourse() != null ? a.getCourse().getCode() : null)
                .courseName(a.getCourse() != null ? a.getCourse().getName() : null)
                .title(a.getTitle())
                .description(a.getDescription())
                .dueAt(a.getDueAt())
                .submissionType(a.getSubmissionType())
                .maxTeamSize(a.getMaxTeamSize())
                .maxScore(a.getMaxScore())
                .groupId(hashidService.encode(a.getAssignmentGroup() != null ? a.getAssignmentGroup().getId() : null))
                .groupName(a.getAssignmentGroup() != null ? a.getAssignmentGroup().getName() : null)
                .moduleId(hashidService.encode(a.getAssignmentModule() != null ? a.getAssignmentModule().getId() : null))
                .moduleName(a.getAssignmentModule() != null ? a.getAssignmentModule().getName() : null)
                .isPublished(a.getIsPublished())
                .teamLockAt(a.getTeamLockAt())
                .rubricCriteria(criteria)
                .build();
    }

    private AssignmentResponse.RubricCriterionResponse toCriterionResponse(RubricCriterion c) {
        return AssignmentResponse.RubricCriterionResponse.builder()
                .id(hashidService.encode(c.getId()))
                .name(c.getName())
                .description(c.getDescription())
                .maxScore(c.getMaxScore())
                .displayOrder(c.getDisplayOrder())
                .build();
    }
}
