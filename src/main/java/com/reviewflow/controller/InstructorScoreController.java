package com.reviewflow.controller;

import com.reviewflow.model.dto.request.CreateInstructorScoreRequest;
import com.reviewflow.model.dto.request.InstructorScoreImportCommitRequest;
import com.reviewflow.model.dto.request.ReopenInstructorScoreRequest;
import com.reviewflow.model.dto.request.UpdateInstructorScoreRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.InstructorScoreImportCommitResponse;
import com.reviewflow.model.dto.response.InstructorScoreImportPreviewResponse;
import com.reviewflow.model.dto.response.InstructorScoreListResponse;
import com.reviewflow.model.dto.response.InstructorScoreResponse;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.CsvImportService;
import com.reviewflow.service.HashidService;
import com.reviewflow.service.InstructorScoreService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InstructorScoreController {

    private final InstructorScoreService instructorScoreService;
    private final CsvImportService csvImportService;
    private final HashidService hashidService;

    @Operation(summary = "Create or replace draft instructor score")
    @PostMapping("/assignments/{id}/instructor-scores")
    public ResponseEntity<ApiResponse<InstructorScoreResponse>> create(
            @PathVariable String id,
            @Valid @RequestBody CreateInstructorScoreRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentId = hashidService.decodeOrThrow(id);
        Long studentId = request.getStudentId() != null ? hashidService.decodeOrThrow(request.getStudentId()) : null;
        Long teamId = request.getTeamId() != null ? hashidService.decodeOrThrow(request.getTeamId()) : null;
        InstructorScoreResponse response = instructorScoreService.create(
                assignmentId,
                user.getUserId(),
                studentId,
                teamId,
                request.getScore(),
                request.getComment());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Operation(summary = "Update a draft instructor score")
    @PutMapping("/instructor-scores/{id}")
    public ResponseEntity<ApiResponse<InstructorScoreResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateInstructorScoreRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        InstructorScoreResponse response = instructorScoreService.update(
                hashidService.decodeOrThrow(id),
                user.getUserId(),
                request.getScore(),
                request.getComment());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "List instructor scores for assignment")
    @GetMapping("/assignments/{id}/instructor-scores")
    public ResponseEntity<ApiResponse<InstructorScoreListResponse>> list(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        InstructorScoreListResponse response = instructorScoreService.listByAssignment(
                hashidService.decodeOrThrow(id),
                user.getUserId(),
                user.getRole(),
                page,
                size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Dry-run CSV score import")
    @PostMapping("/assignments/{id}/instructor-scores/import")
    public ResponseEntity<ApiResponse<InstructorScoreImportPreviewResponse>> dryRunImport(
            @PathVariable String id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        InstructorScoreImportPreviewResponse response = csvImportService.dryRun(
                hashidService.decodeOrThrow(id),
                user.getUserId(),
                file);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Commit CSV score import")
    @PostMapping("/assignments/{id}/instructor-scores/import/commit")
    public ResponseEntity<ApiResponse<InstructorScoreImportCommitResponse>> commitImport(
            @PathVariable String id,
            @Valid @RequestBody InstructorScoreImportCommitRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        InstructorScoreImportCommitResponse response = csvImportService.commit(
                hashidService.decodeOrThrow(id),
                user.getUserId(),
                request.getImportId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Publish single score")
    @PatchMapping("/instructor-scores/{id}/publish")
    public ResponseEntity<ApiResponse<InstructorScoreResponse>> publish(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        InstructorScoreResponse response = instructorScoreService.publish(hashidService.decodeOrThrow(id), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Publish all draft scores for assignment")
    @PostMapping("/assignments/{id}/instructor-scores/publish-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publishAll(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        int count = instructorScoreService.publishAll(hashidService.decodeOrThrow(id), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("publishedCount", count, "message", count + " scores published")));
    }

    @Operation(summary = "Reopen published score")
    @PatchMapping("/instructor-scores/{id}/reopen")
    public ResponseEntity<ApiResponse<InstructorScoreResponse>> reopen(
            @PathVariable String id,
            @Valid @RequestBody ReopenInstructorScoreRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        InstructorScoreResponse response = instructorScoreService.reopen(
                hashidService.decodeOrThrow(id),
                user.getUserId(),
                request.getReason());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
