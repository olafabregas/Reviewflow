package com.reviewflow.admin.controller;

import com.reviewflow.admin.service.AdminStatsService;
import com.reviewflow.shared.exception.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
@Tag(
    name = "Admin Statistics",
    description = "System-wide statistics and analytics for administrators")
public class AdminStatsController {

  private final AdminStatsService adminStatsService;

  @Operation(
      summary = "Get admin statistics",
      description =
          "Get comprehensive system statistics including user counts by role, course counts, "
              + "assignment statistics, team counts, submission counts, and storage usage. "
              + "Results are cached for performance. Admin-only endpoint.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Statistics retrieved successfully",
        content = @Content(schema = @Schema(implementation = Map.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - authentication required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden - ADMIN role required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  @GetMapping
  public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
    return ResponseEntity.ok(ApiResponse.ok(adminStatsService.getStats()));
  }
}
