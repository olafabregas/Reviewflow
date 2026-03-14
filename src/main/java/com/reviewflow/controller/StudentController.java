
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
public class StudentController {

    private final TeamMemberRepository teamMemberRepository;
    private final SubmissionService submissionService;
    private final EvaluationService evaluationService;
    private final RubricScoreRepository rubricScoreRepository;
    private final HashidService hashidService;

    @GetMapping("/invites")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<List<TeamInviteResponse>>> myInvites(
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        List<TeamMember> pending = teamMemberRepository.findByUser_IdAndStatus(user.getUserId(), TeamMemberStatus.PENDING);
        List<TeamInviteResponse> data = pending.stream().map(this::toInviteResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/submissions")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<Page<SubmissionResponse>>> mySubmissions(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            @PageableDefault(size = 20, sort = "uploadedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<SubmissionResponse> page = submissionService.getMySubmissions(user.getUserId(), pageable)
                .map(this::toSubmissionResponse);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

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
        return SubmissionResponse.builder()
                .id(hashidService.encode(s.getId()))
                .teamId(s.getTeam() != null ? hashidService.encode(s.getTeam().getId()) : null)
                .teamName(s.getTeam() != null ? s.getTeam().getName() : null)
                .assignmentId(s.getAssignment() != null ? hashidService.encode(s.getAssignment().getId()) : null)
                .assignmentTitle(s.getAssignment() != null ? s.getAssignment().getTitle() : null)
                .courseCode(s.getAssignment() != null && s.getAssignment().getCourse() != null
                        ? s.getAssignment().getCourse().getCode() : null)
                .versionNumber(s.getVersionNumber())
                .fileName(s.getFileName())
                .fileSizeBytes(s.getFileSizeBytes())
                .isLate(s.getIsLate())
                .uploadedAt(s.getUploadedAt())
                .changeNote(s.getChangeNote())
                .uploadedById(s.getUploadedBy() != null ? hashidService.encode(s.getUploadedBy().getId()) : null)
                .uploadedByName(s.getUploadedBy() != null
                        ? s.getUploadedBy().getFirstName() + " " + s.getUploadedBy().getLastName() : null)
                .build();
    }

    private EvaluationResponse toEvalResponse(Evaluation ev) {
        List<RubricScore> scores = rubricScoreRepository.findByEvaluation_Id(ev.getId());
        BigDecimal maxPossible = BigDecimal.valueOf(scores.stream()
                .mapToInt(s -> s.getCriterion() != null && s.getCriterion().getMaxScore() != null
                        ? s.getCriterion().getMaxScore()
                        : 0)
                .sum());
        
        List<EvaluationResponse.RubricScoreResponse> scoreResponses = scores.stream()
                .map(s -> {
                    BigDecimal maxScore = s.getCriterion() != null && s.getCriterion().getMaxScore() != null
                            ? BigDecimal.valueOf(s.getCriterion().getMaxScore())
                            : BigDecimal.ZERO;
                    return EvaluationResponse.RubricScoreResponse.builder()
                            .id(hashidService.encode(s.getId()))
                            .criterionId(s.getCriterion() != null ? hashidService.encode(s.getCriterion().getId()) : null)
                            .criterionName(s.getCriterion() != null ? s.getCriterion().getName() : null)
                            .maxScore(maxScore)
                            .score(s.getScore())
                            .comment(s.getComment())
                            .build();
                })
                .collect(Collectors.toList());
        
        return EvaluationResponse.builder()
                .id(hashidService.encode(ev.getId()))
                .submissionId(ev.getSubmission() != null ? hashidService.encode(ev.getSubmission().getId()) : null)
                .instructorId(ev.getInstructor() != null ? hashidService.encode(ev.getInstructor().getId()) : null)
                .instructorName(ev.getInstructor() != null
                        ? ev.getInstructor().getFirstName() + " " + ev.getInstructor().getLastName() : null)
                .overallComment(ev.getOverallComment())
                .totalScore(ev.getTotalScore())
                .maxPossibleScore(maxPossible)
                .isDraft(ev.getIsDraft())
                .publishedAt(ev.getPublishedAt())
                .createdAt(ev.getCreatedAt())
                .hasPdf(ev.getPdfPath() != null)
                .rubricScores(scoreResponses)
                .build();
    }
}
