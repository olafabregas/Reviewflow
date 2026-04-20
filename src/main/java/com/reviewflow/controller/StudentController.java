
package com.reviewflow.controller;

import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.EvaluationResponse;
import com.reviewflow.model.dto.response.SubmissionResponse;
import com.reviewflow.model.dto.response.TeamInviteResponse;
import com.reviewflow.model.entity.*;
import com.reviewflow.repository.RubricScoreRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.EvaluationService;
import com.reviewflow.service.SubmissionService;
import com.reviewflow.service.HashidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static com.reviewflow.controller.EvaluationController.getEvaluationResponse;

@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
@Tag(name = "Student", description = "Student personal dashboard and submissions")
public class StudentController {

    private final TeamMemberRepository teamMemberRepository;
    private final SubmissionService submissionService;
    private final EvaluationService evaluationService;
    private final RubricScoreRepository rubricScoreRepository;
    private final HashidService hashidService;

    @Operation(
        summary = "Get team invitations",
        description = "Get all pending team invitations for the authenticated student. " +
                    "Shows teams the student has been invited to join."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Team invitations retrieved successfully",
            content = @Content(schema = @Schema(implementation = List.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/invites")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<List<TeamInviteResponse>>> myInvites(
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        List<TeamMember> pending = teamMemberRepository.findByUser_IdAndStatus(user.getUserId(), TeamMemberStatus.PENDING);
        List<TeamInviteResponse> data = pending.stream().map(this::toInviteResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
        summary = "Get my submissions",
        description = "Get paginated list of all submissions uploaded by the authenticated student. " +
                    "Includes individual and team submissions. Sorted by uploadedAt descending."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Student submissions retrieved successfully",
            content = @Content(schema = @Schema(implementation = Page.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/submissions")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<Page<SubmissionResponse>>> mySubmissions(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            @PageableDefault(size = 20, sort = "uploadedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<SubmissionResponse> page = submissionService.getMySubmissions(user.getUserId(), pageable)
                .map(this::toSubmissionResponse);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @Operation(
        summary = "Get my evaluations",
        description = "Get paginated list of all evaluations for the authenticated student's submissions. " +
                    "Shows only published evaluations (isDraft=false). Sorted by createdAt descending."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Student evaluations retrieved successfully",
            content = @Content(schema = @Schema(implementation = Page.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/evaluations")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<Page<EvaluationResponse>>> myEvaluations(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<EvaluationResponse> page = evaluationService.getMyEvaluations(user.getUserId(), pageable)
                .map(this::toEvalResponse);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    private TeamInviteResponse toInviteResponse(TeamMember m) {
        return TeamInviteResponse.builder()
                .teamMemberId(hashidService.encode(m.getId()))
                .teamId(m.getTeam() != null ? hashidService.encode(m.getTeam().getId()) : null)
                .teamName(m.getTeam() != null ? m.getTeam().getName() : null)
                .assignmentId(m.getAssignment() != null ? hashidService.encode(m.getAssignment().getId()) : null)
                .assignmentTitle(m.getAssignment() != null ? m.getAssignment().getTitle() : null)
                .courseCode(m.getAssignment() != null && m.getAssignment().getCourse() != null
                        ? m.getAssignment().getCourse().getCode() : null)
                .invitedByName(m.getInvitedBy() != null
                        ? m.getInvitedBy().getFirstName() + " " + m.getInvitedBy().getLastName() : null)
                .createdAt(m.getJoinedAt())
                .build();
    }

    private SubmissionResponse toSubmissionResponse(Submission s) {
        return SubmissionResponse.from(s, hashidService);
    }

    private EvaluationResponse toEvalResponse(Evaluation ev) {
        List<RubricScore> scores = rubricScoreRepository.findByEvaluation_Id(ev.getId());
        BigDecimal maxPossible = scores.stream()
                .map(s -> s.getCriterion() != null && s.getCriterion().getMaxScore() != null
                        ? BigDecimal.valueOf(s.getCriterion().getMaxScore())
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return getEvaluationResponse(ev, scores, maxPossible, hashidService);
    }
}
