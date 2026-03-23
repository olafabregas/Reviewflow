package com.reviewflow.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

import com.reviewflow.model.dto.request.CreateAssignmentRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.AssignmentResponse;
import com.reviewflow.model.dto.response.EvaluationResponse;
import com.reviewflow.model.dto.response.GradebookEntryResponse;
import com.reviewflow.model.dto.response.SubmissionResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Evaluation;
import com.reviewflow.model.entity.RubricCriterion;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.AssignmentService;
import com.reviewflow.service.HashidService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final HashidService hashidService;

    @GetMapping("/courses/{courseId}/assignments")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> listByCourse(
            @PathVariable String courseId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long courseIdLong = hashidService.decodeOrThrow(courseId);
        List<Assignment> list = assignmentService.listAssignmentsForCourse(courseIdLong, user.getUserId(), user.getRole());
        List<AssignmentResponse> data = list.stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/courses/{courseId}/assignments")
    public ResponseEntity<ApiResponse<AssignmentResponse>> create(
            @PathVariable String courseId,
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long courseIdLong = hashidService.decodeOrThrow(courseId);
        Assignment a = assignmentService.createAssignment(
                courseIdLong, request.getTitle(), request.getDescription(), request.getDueAt(),
            request.getMaxTeamSize(), request.getSubmissionType(), request.getTeamLockAt(),
            request.getIsPublished(), user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toResponse(a)));
    }

    @GetMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<AssignmentResponse>> get(
            @PathVariable String id, 
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        Assignment a = assignmentService.getAssignmentByIdWithAccessControl(assignmentId, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(a)));
    }

    @PutMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<AssignmentResponse>> update(
            @PathVariable String id,
            @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        Assignment a = assignmentService.updateAssignment(
                assignmentId, request.getTitle(), request.getDescription(), request.getDueAt(),
            request.getMaxTeamSize(), request.getSubmissionType(), request.getTeamLockAt(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(a)));
    }

    @PatchMapping("/assignments/{id}/publish")
    public ResponseEntity<ApiResponse<AssignmentResponse>> publish(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        Assignment a = assignmentService.publishAssignment(assignmentId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(a)));
    }

// endpoint to get all assignments for the authenticated user (both instructor and student)

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

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        assignmentService.deleteAssignment(assignmentId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Assignment deleted")));
    }

    @PostMapping("/assignments/{id}/rubric")
    public ResponseEntity<ApiResponse<AssignmentResponse.RubricCriterionResponse>> addRubric(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (body.get("maxScore") == null) {
            throw new IllegalArgumentException("maxScore is required");
        }
        if (body.get("displayOrder") == null) {
            throw new IllegalArgumentException("displayOrder is required");
        }
        
        int maxScore = ((Number) body.get("maxScore")).intValue();
        int displayOrder = ((Number) body.get("displayOrder")).intValue();
        RubricCriterion c = assignmentService.addRubricCriteria(assignmentId, name, description, maxScore, displayOrder, user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toCriterionResponse(c)));
    }

    @PutMapping("/assignments/{id}/rubric/{criterionId}")
    public ResponseEntity<ApiResponse<AssignmentResponse.RubricCriterionResponse>> updateRubric(
            @PathVariable String id,
            @PathVariable String criterionId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        Long criterionIdLong = hashidService.decodeOrThrow(criterionId);
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        Integer maxScore = body.get("maxScore") != null ? ((Number) body.get("maxScore")).intValue() : null;
        Integer displayOrder = body.get("displayOrder") != null ? ((Number) body.get("displayOrder")).intValue() : null;
        RubricCriterion c = assignmentService.updateRubricCriteria(assignmentId, criterionIdLong, name, description, maxScore, displayOrder, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toCriterionResponse(c)));
    }

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
        return SubmissionResponse.builder()
                .id(hashidService.encode(s.getId()))
            .submissionType(s.getAssignment() != null ? s.getAssignment().getSubmissionType() : null)
            .studentId(hashidService.encode(s.getStudent() != null ? s.getStudent().getId() : null))
                .teamId(hashidService.encode(s.getTeam() != null ? s.getTeam().getId() : null))
                .teamName(s.getTeam() != null ? s.getTeam().getName() : null)
                .assignmentId(hashidService.encode(s.getAssignment() != null ? s.getAssignment().getId() : null))
                .assignmentTitle(s.getAssignment() != null ? s.getAssignment().getTitle() : null)
                .courseCode(s.getAssignment() != null && s.getAssignment().getCourse() != null ? s.getAssignment().getCourse().getCode() : null)
                .versionNumber(s.getVersionNumber())
                .fileName(s.getFileName())
                .fileSizeBytes(s.getFileSizeBytes())
                .isLate(s.getIsLate())
                .uploadedAt(s.getUploadedAt())
                .changeNote(s.getChangeNote())
                .uploadedById(hashidService.encode(s.getUploadedBy() != null ? s.getUploadedBy().getId() : null))
                .uploadedByName(s.getUploadedBy() != null ? s.getUploadedBy().getFirstName() + " " + s.getUploadedBy().getLastName() : null)
                .build();
    }

    private EvaluationResponse toEvalResponse(Evaluation e) {
        List<EvaluationResponse.RubricScoreResponse> scores = e.getRubricScores() != null
                ? e.getRubricScores().stream().map(rs -> EvaluationResponse.RubricScoreResponse.builder()
                .id(hashidService.encode(rs.getId()))
                .criterionId(hashidService.encode(rs.getCriterion() != null ? rs.getCriterion().getId() : null))
                .criterionName(rs.getCriterion() != null ? rs.getCriterion().getName() : null)
                .maxScore(rs.getCriterion() != null ? java.math.BigDecimal.valueOf(rs.getCriterion().getMaxScore()) : null)
                .score(rs.getScore())
                .comment(rs.getComment())
                .build()).collect(Collectors.toList())
                : List.of();
        return EvaluationResponse.builder()
                .id(hashidService.encode(e.getId()))
                .submissionId(hashidService.encode(e.getSubmission() != null ? e.getSubmission().getId() : null))
                .instructorId(hashidService.encode(e.getInstructor() != null ? e.getInstructor().getId() : null))
                .instructorName(e.getInstructor() != null ? e.getInstructor().getFirstName() + " " + e.getInstructor().getLastName() : null)
                .overallComment(e.getOverallComment())
                .totalScore(e.getTotalScore())
                .isDraft(e.getIsDraft())
                .publishedAt(e.getPublishedAt())
                .createdAt(e.getCreatedAt())
                .hasPdf(e.getPdfPath() != null && !e.getPdfPath().isBlank())
                .rubricScores(scores)
                .build();
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
