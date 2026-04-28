package com.reviewflow.controller;

import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.GradeExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Grade Export", description = "Grade export and reporting functionality")
public class GradeExportController {

  private final GradeExportService gradeExportService;

  @Operation(
      summary = "Export grades to CSV",
      description =
          "Export all grades for an assignment in a course as CSV file. "
              + "Instructor-only. Returns file with Content-Disposition: attachment.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Grades exported successfully as CSV",
        content = @Content(mediaType = "text/csv")),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Bad Request - invalid course or assignment ID",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - authentication required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden - INSTRUCTOR or ADMIN role required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Not Found - course or assignment does not exist",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  @GetMapping("/courses/{courseId}/evaluations/export")
  @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<byte[]> export(
      @PathVariable String courseId,
      @RequestParam String assignmentId,
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    GradeExportService.ExportResult result =
        gradeExportService.export(courseId, assignmentId, user.getUserId());

    ContentDisposition disposition =
        ContentDisposition.attachment().filename(result.filename()).build();

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .body(result.bytes());
  }
}
