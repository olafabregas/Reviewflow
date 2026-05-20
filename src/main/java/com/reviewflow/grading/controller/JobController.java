package com.reviewflow.grading.controller;

import com.reviewflow.auth.annotation.RequiresStepUp;
import com.reviewflow.grading.dto.response.JobErrorDownloadResponse;
import com.reviewflow.grading.dto.response.JobStatusDto;
import com.reviewflow.grading.service.ImportJobService;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.exception.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'SYSTEM_ADMIN')")
public class JobController {

  private final ImportJobService importJobService;

  @Operation(summary = "Get CSV import job status")
  @GetMapping("/{jobId}/status")
  public ResponseEntity<ApiResponse<JobStatusDto>> getStatus(
      @PathVariable String jobId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    JobStatusDto response =
        importJobService.getStatus(jobId, user.getUserId(), user.getRole());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(summary = "SSE progress stream during CSV validation")
  @GetMapping(value = "/{jobId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter progress(
      @PathVariable String jobId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    return importJobService.subscribeProgress(jobId, user.getUserId(), user.getRole());
  }

  @Operation(summary = "Presigned URL for validation error CSV")
  @GetMapping("/{jobId}/errors/download")
  public ResponseEntity<ApiResponse<JobErrorDownloadResponse>> downloadErrors(
      @PathVariable String jobId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    JobErrorDownloadResponse response =
        importJobService.getErrorDownloadUrl(jobId, user.getUserId(), user.getRole());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(summary = "Commit validated CSV import")
  @PostMapping("/{jobId}/commit")
  @RequiresStepUp(maxAgeSeconds = 300)
  public ResponseEntity<ApiResponse<JobStatusDto>> commit(
      @PathVariable String jobId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    JobStatusDto response = importJobService.commit(jobId, user.getUserId(), user.getRole());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
  }
}
