package com.reviewflow.grading.controller;

import com.reviewflow.shared.exception.ApiResponse;
import com.reviewflow.grading.dto.response.ClassRosterDto;
import com.reviewflow.grading.dto.response.GradeOverviewDto;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.grading.service.GradeCalculationService;
import com.reviewflow.shared.util.HashidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Grade Overview", description = "Student overview and class roster grade views")
public class GradeOverviewController {

  private final GradeCalculationService gradeCalculationService;
  private final HashidService hashidService;

  @Operation(summary = "Get my grade overview")
  @GetMapping("/courses/{courseId}/grade-overview")
  public ResponseEntity<ApiResponse<GradeOverviewDto>> getMyOverview(
      @PathVariable String courseId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    GradeOverviewDto response =
        gradeCalculationService.calculateMyOverview(
            hashidService.decodeOrThrow(courseId), user.getUserId(), user.getRole());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(summary = "Get grade overview for a specific student")
  @GetMapping("/courses/{courseId}/grade-overview/student/{studentId}")
  public ResponseEntity<ApiResponse<GradeOverviewDto>> getStudentOverview(
      @PathVariable String courseId,
      @PathVariable String studentId,
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    GradeOverviewDto response =
        gradeCalculationService.calculateStudentOverview(
            hashidService.decodeOrThrow(courseId),
            hashidService.decodeOrThrow(studentId),
            user.getUserId(),
            user.getRole());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(summary = "Get grade overview roster for a course")
  @GetMapping("/courses/{courseId}/grade-overview/roster")
  public ResponseEntity<ApiResponse<ClassRosterDto>> getRoster(
      @PathVariable String courseId,
      @RequestParam(defaultValue = "standing") String sortBy,
      @RequestParam(defaultValue = "asc") String direction,
      @RequestParam(defaultValue = "false") boolean atRiskOnly,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    ClassRosterDto response =
        gradeCalculationService.calculateRoster(
            hashidService.decodeOrThrow(courseId),
            user.getUserId(),
            user.getRole(),
            sortBy,
            direction,
            atRiskOnly,
            page,
            size);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
