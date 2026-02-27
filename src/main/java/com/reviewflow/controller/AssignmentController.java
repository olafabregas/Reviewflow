package com.reviewflow.controller;

import com.reviewflow.model.dto.request.CreateAssignmentRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.AssignmentResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.RubricCriterion;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @GetMapping("/courses/{courseId}/assignments")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> listByCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        List<Assignment> list = assignmentService.listAssignmentsForCourse(courseId, user.getUserId(), user.getRole());
        List<AssignmentResponse> data = list.stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/courses/{courseId}/assignments")
    public ResponseEntity<ApiResponse<AssignmentResponse>> create(
            @PathVariable Long courseId,
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Assignment a = assignmentService.createAssignment(
                courseId, request.getTitle(), request.getDescription(), request.getDueAt(),
                request.getMaxTeamSize(), request.getTeamLockAt(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(a)));
    }

    @GetMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<AssignmentResponse>> get(@PathVariable Long id, @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Assignment a = assignmentService.getAssignmentById(id);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(a)));
    }

    @PutMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<AssignmentResponse>> update(
            @PathVariable Long id,
            @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Assignment a = assignmentService.updateAssignment(
                id, request.getTitle(), request.getDescription(), request.getDueAt(),
                request.getMaxTeamSize(), request.getTeamLockAt(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(a)));
    }

    @PatchMapping("/assignments/{id}/publish")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publish(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        boolean published = body != null && Boolean.TRUE.equals(body.get("published"));
        assignmentService.publishAssignment(id, published, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("published", published)));
    }

    @PostMapping("/assignments/{id}/rubric")
    public ResponseEntity<ApiResponse<AssignmentResponse.RubricCriterionResponse>> addRubric(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        int maxScore = body.get("maxScore") != null ? ((Number) body.get("maxScore")).intValue() : 0;
        int displayOrder = body.get("displayOrder") != null ? ((Number) body.get("displayOrder")).intValue() : 0;
        RubricCriterion c = assignmentService.addRubricCriteria(id, name, description, maxScore, displayOrder, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toCriterionResponse(c)));
    }

    @PutMapping("/assignments/{id}/rubric/{criterionId}")
    public ResponseEntity<ApiResponse<AssignmentResponse.RubricCriterionResponse>> updateRubric(
            @PathVariable Long id,
            @PathVariable Long criterionId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        Integer maxScore = body.get("maxScore") != null ? ((Number) body.get("maxScore")).intValue() : null;
        Integer displayOrder = body.get("displayOrder") != null ? ((Number) body.get("displayOrder")).intValue() : null;
        RubricCriterion c = assignmentService.updateRubricCriteria(id, criterionId, name, description, maxScore, displayOrder, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toCriterionResponse(c)));
    }

    @DeleteMapping("/assignments/{id}/rubric/{criterionId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteRubric(
            @PathVariable Long id,
            @PathVariable Long criterionId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        assignmentService.deleteRubricCriterion(id, criterionId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Criterion deleted")));
    }

    private AssignmentResponse toResponse(Assignment a) {
        List<AssignmentResponse.RubricCriterionResponse> criteria = a.getRubricCriteria() != null
                ? a.getRubricCriteria().stream().map(this::toCriterionResponse).collect(Collectors.toList())
                : List.of();
        return AssignmentResponse.builder()
                .id(a.getId())
                .courseId(a.getCourse() != null ? a.getCourse().getId() : null)
                .title(a.getTitle())
                .description(a.getDescription())
                .dueAt(a.getDueAt())
                .maxTeamSize(a.getMaxTeamSize())
                .isPublished(a.getIsPublished())
                .teamLockAt(a.getTeamLockAt())
                .rubricCriteria(criteria)
                .build();
    }

    private AssignmentResponse.RubricCriterionResponse toCriterionResponse(RubricCriterion c) {
        return AssignmentResponse.RubricCriterionResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .maxScore(c.getMaxScore())
                .displayOrder(c.getDisplayOrder())
                .build();
    }
}
