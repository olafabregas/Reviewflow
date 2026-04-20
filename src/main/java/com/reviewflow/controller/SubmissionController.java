package com.reviewflow.controller;

import java.util.List;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.PreviewResponseDto;
import com.reviewflow.model.dto.response.SubmissionResponse;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.HashidService;
import com.reviewflow.service.SubmissionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
@Tag(
    name = "Submissions",
    description = "Student submission management. Supports uploading, retrieving, downloading, and previewing " +
                "assignment submissions. Tracks version history and file metadata."
)
public class SubmissionController {

    private final SubmissionService submissionService;
    private final HashidService hashidService;

    @Operation(
        summary = "Upload submission",
        description = "Upload student or team assignment submission. Required: assignmentId, file. " +
                    "Optional: teamId (for team submissions), changeNote (version annotation). " +
                    "Returns HTTP 201 with submission details including version number."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Submission uploaded successfully",
            content = @Content(schema = @Schema(implementation = SubmissionResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - missing required parameters or invalid file",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - cannot submit for this assignment/team",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "413",
            description = "Payload Too Large - file exceeds maximum size",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<SubmissionResponse>> upload(
            @RequestParam(required = false) String teamId,
            @RequestParam String assignmentId,
            @RequestParam(required = false) String changeNote,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamIdLong = teamId != null ? hashidService.decodeOrThrow(teamId) : null;
        Long assignmentIdLong = hashidService.decodeOrThrow(assignmentId);
        Submission sub = submissionService.upload(teamIdLong, assignmentIdLong, changeNote, file, user.getUserId());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.ok(toResponse(sub)));
    }

    @Operation(
        summary = "Get submission details",
        description = "Retrieve detailed submission information by ID including file metadata, version number, " +
                    "and upload timestamp. Students can only view their own submissions."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Submission details retrieved",
            content = @Content(schema = @Schema(implementation = SubmissionResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - no access to this submission",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - submission does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubmissionResponse>> get(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long submissionId = hashidService.decodeOrThrow(id);
        Submission sub = submissionService.getSubmission(submissionId, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(sub)));
    }

    @Operation(
        summary = "Get submission version history",
        description = "Retrieve version history for team's assignment submission. Shows all previous uploads with " +
                    "version numbers, change notes, and upload timestamps. Useful for tracking resubmissions."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Version history retrieved",
            content = @Content(schema = @Schema(implementation = List.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - no access to this team's submissions",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - team or assignment does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/teams/{teamId}/assignments/{assignmentId}/history")
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> history(
            @PathVariable String teamId,
            @PathVariable String assignmentId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamIdLong = hashidService.decodeOrThrow(teamId);
        Long assignmentIdLong = hashidService.decodeOrThrow(assignmentId);
        List<SubmissionResponse> data = submissionService
                .getVersionHistory(teamIdLong, assignmentIdLong, user.getUserId(), user.getRole())
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
        summary = "Download submission file",
        description = "Download the submitted file as binary attachment. Resolves from S3 storage. " +
                    "Students can download their own submissions, instructors can download any for their course."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "File downloaded successfully",
            content = @Content(mediaType = "application/octet-stream")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - no access to download this file",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - submission or file does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long submissionId = hashidService.decodeOrThrow(id);
        Submission sub = submissionService.getSubmission(submissionId, user.getUserId(), user.getRole());
        Resource resource = submissionService.downloadSubmission(submissionId, user.getUserId(), user.getRole());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sub.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(
        summary = "Get preview URL for submission",
        description = "Get URL to preview file content without downloading. Supports PDF and image files. " +
                    "Returns temporary signed URL from S3 storage valid for limited time."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Preview URL generated successfully",
            content = @Content(schema = @Schema(implementation = PreviewResponseDto.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - file type not previewable",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - no access to this submission",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - submission or file does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/{id}/preview")
    public ResponseEntity<ApiResponse<PreviewResponseDto>> preview(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        PreviewResponseDto previewDto = submissionService.getPreviewUrl(id, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.ok(previewDto));
    }

    private SubmissionResponse toResponse(Submission s) {
        return SubmissionResponse.from(s, hashidService);
    }
}
