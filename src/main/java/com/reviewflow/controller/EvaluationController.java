package com.reviewflow.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reviewflow.model.dto.request.CreateEvaluationRequest;
import com.reviewflow.model.dto.request.PatchCommentRequest;
import com.reviewflow.model.dto.request.PatchScoreRequest;
import com.reviewflow.model.dto.request.UpdateScoresRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.EvaluationResponse;
import com.reviewflow.model.dto.response.PreviewResponseDto;
import com.reviewflow.model.entity.Evaluation;
import com.reviewflow.model.entity.RubricScore;
import com.reviewflow.repository.RubricScoreRepository;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.EvaluationService;
import com.reviewflow.util.HashidService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
@Tag(
    name = "Evaluations",
    description = "Assignment submission grading and evaluation. Supports creating evaluations, scoring using rubrics, " +
                "publishing grades, and generating evaluation PDFs. Instructor-only access."
)
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final RubricScoreRepository rubricScoreRepository;
    private final HashidService hashidService;

    @Operation(
        summary = "Create evaluation",
        description = "Create new evaluation for a submission. Instructor-only. Initializes as draft with empty scores. " +
                    "Returns HTTP 201 with evaluation details."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Evaluation created successfully",
            content = @Content(schema = @Schema(implementation = EvaluationResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - invalid submission ID",
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
            responseCode = "409",
            description = "Conflict - evaluation already exists for this submission",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<EvaluationResponse>> create(
            @Valid @RequestBody CreateEvaluationRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long submissionId = hashidService.decodeOrThrow(request.getSubmissionId());
        Evaluation ev = evaluationService.createEvaluation(submissionId, user.getUserId());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.ok(toResponse(ev)));
    }

    @Operation(
        summary = "Get evaluation details",
        description = "Retrieve full evaluation details including all rubric scores, comments, and publication status. " +
                    "Students can only view published evaluations for their submissions."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Evaluation retrieved successfully",
            content = @Content(schema = @Schema(implementation = EvaluationResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - no access to this evaluation",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - evaluation does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/{id}")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<EvaluationResponse>> get(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long evalId = hashidService.decodeOrThrow(id);
        Evaluation ev = evaluationService.getEvaluationWithAccessControl(evalId, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(ev)));
    }

    @Operation(
        summary = "Set all rubric scores",
        description = "Update all rubric criterion scores for an evaluation at once. Required: scores map (criterionId → score). " +
                    "Instructor-only. Cannot update published evaluations."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Scores updated successfully",
            content = @Content(schema = @Schema(implementation = EvaluationResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - invalid score data",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - INSTRUCTOR role required or evaluation published",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - evaluation or criterion does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PutMapping("/{id}/scores")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<EvaluationResponse>> setScores(
            @PathVariable String id,
            @Valid @RequestBody UpdateScoresRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long evalId = hashidService.decodeOrThrow(id);
        Evaluation ev = evaluationService.setScores(evalId, request.getScores(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(ev)));
    }

    @Operation(
        summary = "Update single rubric score",
        description = "Update score and comment for a single rubric criterion. Instructor-only. " +
                    "Cannot update published evaluations."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Score updated successfully",
            content = @Content(schema = @Schema(implementation = EvaluationResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - invalid score data",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - INSTRUCTOR role required or evaluation published",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - evaluation or criterion does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/{id}/scores/{criterionId}")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<EvaluationResponse>> patchScore(
            @PathVariable String id,
            @PathVariable String criterionId,
            @Valid @RequestBody PatchScoreRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long evalId = hashidService.decodeOrThrow(id);
        Long criterionIdLong = hashidService.decodeOrThrow(criterionId);
        Evaluation ev = evaluationService.patchScore(evalId, criterionIdLong, request.getScore(), request.getComment(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(ev)));
    }

    @Operation(
        summary = "Set overall evaluation comment",
        description = "Update the overall evaluation comment visible to students. Instructor-only. " +
                    "Can be updated even after publishing."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Comment set successfully",
            content = @Content(schema = @Schema(implementation = EvaluationResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - invalid comment data",
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
            description = "Not Found - evaluation does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/{id}/comment")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<EvaluationResponse>> setComment(
            @PathVariable String id,
            @RequestBody PatchCommentRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long evalId = hashidService.decodeOrThrow(id);
        Evaluation ev = evaluationService.setComment(evalId, request.getOverallComment(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(ev)));
    }

    @Operation(
        summary = "Publish evaluation",
        description = "Publish evaluation making it visible to students. Transition from draft to published. " +
                    "Instructor-only. Triggers student notifications."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Evaluation published successfully",
            content = @Content(schema = @Schema(implementation = EvaluationResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - INSTRUCTOR role required or already published",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - evaluation does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<EvaluationResponse>> publish(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long evalId = hashidService.decodeOrThrow(id);
        Evaluation ev = evaluationService.publishEvaluation(evalId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(ev)));
    }

    @Operation(
        summary = "Reopen evaluation",
        description = "Reopen a published evaluation for editing. Transition from published to draft. " +
                    "Instructor-only. Hides grades from students again."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Evaluation reopened successfully",
            content = @Content(schema = @Schema(implementation = EvaluationResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - INSTRUCTOR role required or not published",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - evaluation does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/{id}/reopen")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<EvaluationResponse>> reopen(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long evalId = hashidService.decodeOrThrow(id);
        Evaluation ev = evaluationService.reopenEvaluation(evalId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(ev)));
    }
    @Operation(
        summary = "Generate evaluation PDF",
        description = "Asynchronously generate PDF of evaluation for formal grading records. " +
                    "Returns job ID to poll for completion. Instructor-only."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "PDF generation job started successfully",
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
            description = "Not Found - evaluation does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })    @PostMapping("/{id}/pdf")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<ApiResponse<Map<String, String>>> generatePdf(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long evalId = hashidService.decodeOrThrow(id);
        Evaluation ev = evaluationService.generatePdf(evalId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("pdfPath", ev.getPdfPath())));
    }

    @Operation(
        summary = "Download evaluation PDF",
        description = "Download the generated evaluation PDF as binary attachment. " +
                    "Returns PDF file with Content-Disposition: attachment header."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "PDF file downloaded successfully",
            content = @Content(mediaType = "application/pdf")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - INSTRUCTOR role required or not published",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - evaluation or PDF does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/{id}/pdf")
    @SuppressWarnings("NullableProblems")
    public ResponseEntity<Resource> downloadPdf(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long evalId = hashidService.decodeOrThrow(id);
        Resource resource = evaluationService.downloadPdf(evalId, user.getUserId(), user.getRole());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"evaluation_" + evalId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @Operation(
        summary = "Preview evaluation PDF",
        description = "Get a preview URL for evaluation PDF from S3 storage. Returns pre-signed S3 URL. " +
                    "Works for both published and draft evaluations."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Preview URL generated successfully",
            content = @Content(schema = @Schema(implementation = PreviewResponseDto.class))
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
            description = "Not Found - evaluation or PDF does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/{id}/pdf/preview")
    public ResponseEntity<ApiResponse<PreviewResponseDto>> previewPdf(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        PreviewResponseDto previewDto = evaluationService.getPdfPreviewUrl(id, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.ok(previewDto));
    }

    private EvaluationResponse toResponse(Evaluation ev) {
        List<RubricScore> scores = rubricScoreRepository.findByEvaluation_Id(ev.getId());
        BigDecimal maxPossible = scores.stream()
                .map(s -> s.getCriterion() != null && s.getCriterion().getMaxScore() != null
                ? BigDecimal.valueOf(s.getCriterion().getMaxScore())
                : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return getEvaluationResponse(ev, scores, maxPossible, hashidService);
    }

    static EvaluationResponse getEvaluationResponse(Evaluation ev, List<RubricScore> scores, BigDecimal maxPossible, HashidService hashidService) {
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
