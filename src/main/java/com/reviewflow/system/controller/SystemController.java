package com.reviewflow.system.controller;

import com.reviewflow.auth.annotation.RequiresStepUp;
import com.reviewflow.shared.exception.ApiResponse;
import com.reviewflow.shared.dto.CacheEvictResponse;
import jakarta.validation.Valid;
import com.reviewflow.shared.dto.CacheStatsDto;
import com.reviewflow.shared.dto.ForceLogoutResponse;
import com.reviewflow.shared.dto.ReopenEvaluationResponse;
import com.reviewflow.shared.dto.SecurityEventDto;
import com.reviewflow.shared.dto.UnlockTeamResponse;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.system.service.SystemService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "System Admin",
    description = "PRD-09: Platform operator endpoints - SYSTEM_ADMIN role only")
public class SystemController {

  private final SystemService systemService;
  private final HashidService hashidService;

  /** PRD-09 Flow B: Get cache statistics */
  @GetMapping("/cache/stats")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  @Operation(
      summary = "Get cache statistics",
      description =
          "Returns hit/miss stats for all 5 caches (adminStats, unreadCount, userCourses,"
              + " assignmentDetail, courseGradeGroups)")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Cache statistics retrieved successfully",
        content = @Content(schema = @Schema(implementation = Map.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - authentication required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden - SYSTEM_ADMIN role required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  public ResponseEntity<ApiResponse<List<CacheStatsDto>>> getCacheStats() {
    return ResponseEntity.ok(ApiResponse.ok(systemService.getCacheStats()));
  }

  /** PRD-09 Flow B: Evict a cache with 60-second throttle protection */
  @PostMapping("/cache/evict/{cacheName}")
  @RequiresStepUp(maxAgeSeconds = 300)
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  @Operation(
      summary = "Evict cache by name",
      description =
          "Clears all entries from named cache. Throttled: max 1 eviction per 60 seconds per"
              + " cache.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Cache evicted successfully",
        content = @Content(schema = @Schema(implementation = Map.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Bad Request - invalid cache name or throttle limit exceeded",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - authentication required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden - SYSTEM_ADMIN role required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  public ResponseEntity<ApiResponse<CacheEvictResponse>> evictCache(
      @PathVariable String cacheName, Authentication authentication) {

    Long actorId = extractUserIdFromAuthentication(authentication);
    return ResponseEntity.ok(ApiResponse.ok(systemService.evictCache(cacheName, actorId)));
  }

  /** PRD-09 Flow C: Get safe (non-secret) configuration */
  @GetMapping("/config")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  @Operation(
      summary = "Get safe configuration",
      description = "Returns whitelisted configuration properties only (no secrets)")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Configuration retrieved successfully",
        content = @Content(schema = @Schema(implementation = Map.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - authentication required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden - SYSTEM_ADMIN role required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  public ResponseEntity<ApiResponse<Map<String, String>>> getSafeConfig() {
    return ResponseEntity.ok(ApiResponse.ok(systemService.getSafeConfig()));
  }

  /** PRD-18: moderation — list all conversations in a course. */
  @GetMapping("/courses/{courseId}/conversations")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  @Operation(summary = "List course conversations (moderation)")
  public ResponseEntity<ApiResponse<Map<String, Object>>> moderationListCourseConversations(
      @PathVariable String courseId,
      Authentication authentication,
      HttpServletRequest request) {
    Long courseIdLong = hashidService.decodeOrThrow(courseId);
    Long actorId = extractUserIdFromAuthentication(authentication);
    return ResponseEntity.ok(
        ApiResponse.ok(
            systemService.moderationListCourseConversations(
                courseIdLong, actorId, clientIp(request))));
  }

  /** PRD-18: moderation — full message history for a conversation. */
  @GetMapping("/conversations/{conversationId}/messages")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  @Operation(summary = "List conversation messages (moderation)")
  public ResponseEntity<ApiResponse<Map<String, Object>>> moderationListConversationMessages(
      @PathVariable String conversationId,
      Authentication authentication,
      HttpServletRequest request) {
    Long convId = hashidService.decodeOrThrow(conversationId);
    Long actorId = extractUserIdFromAuthentication(authentication);
    return ResponseEntity.ok(
        ApiResponse.ok(
            systemService.moderationListConversationMessages(
                convId, actorId, clientIp(request))));
  }

  /** PRD-09 Flow D: Get security events */
  @GetMapping("/security/events")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  @Operation(
      summary = "Get security events",
      description = "Returns recent security events (failed logins, rate limits, blocked uploads)")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Security events retrieved successfully",
        content = @Content(schema = @Schema(implementation = Map.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - authentication required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden - SYSTEM_ADMIN role required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  public ResponseEntity<ApiResponse<List<SecurityEventDto>>> getSecurityEvents(
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(
        ApiResponse.ok(systemService.getSecurityEvents(Math.min(limit, 500))));
  }

  /** PRD-09 Flow E: Force logout a user */
  @PostMapping("/users/{targetUserId}/force-logout")
  @RequiresStepUp(maxAgeSeconds = 300)
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  @Operation(
      summary = "Force logout a user",
      description = "Revoke all tokens for target user and close their sessions")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "User forced logout successfully",
        content = @Content(schema = @Schema(implementation = Map.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Bad Request - invalid user ID or reason",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - authentication required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden - SYSTEM_ADMIN role required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Not Found - user does not exist",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  public ResponseEntity<ApiResponse<ForceLogoutResponse>> forceLogout(
      @PathVariable String targetUserId,
      @Valid @RequestBody ForceLogoutRequest request,
      Authentication authentication) {

    Long actorId = extractUserIdFromAuthentication(authentication);
    return ResponseEntity.ok(
        ApiResponse.ok(
            systemService.forceLogout(targetUserId, actorId, request.getReason())));
  }

  /** PRD-09 Flow F: Unlock a team (system override) */
  @PostMapping("/teams/{teamId}/unlock")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  @Operation(summary = "Unlock a team", description = "System override - unlocks a locked team")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Team unlocked successfully",
        content = @Content(schema = @Schema(implementation = Map.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Bad Request - invalid team ID or reason",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - authentication required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden - SYSTEM_ADMIN role required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Not Found - team does not exist",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  public ResponseEntity<ApiResponse<UnlockTeamResponse>> unlockTeam(
      @PathVariable String teamId,
      @Valid @RequestBody UnlockTeamRequest request,
      Authentication authentication) {

    Long actorId = extractUserIdFromAuthentication(authentication);
    return ResponseEntity.ok(
        ApiResponse.ok(systemService.unlockTeam(teamId, actorId, request.getReason())));
  }

  /** PRD-09 Flow G: Reopen an evaluation (system override) */
  @PostMapping("/evaluations/{evaluationId}/reopen")
  @RequiresStepUp(maxAgeSeconds = 300)
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  @Operation(
      summary = "Reopen an evaluation",
      description = "System override - reopens a published evaluation for instructor edits")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Evaluation reopened successfully",
        content = @Content(schema = @Schema(implementation = Map.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Bad Request - invalid evaluation ID or reason",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized - authentication required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Forbidden - SYSTEM_ADMIN role required",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Not Found - evaluation does not exist",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
  })
  public ResponseEntity<ApiResponse<ReopenEvaluationResponse>> reopenEvaluation(
      @PathVariable String evaluationId,
      @Valid @RequestBody ReopenEvaluationRequest request,
      Authentication authentication) {

    Long actorId = extractUserIdFromAuthentication(authentication);
    return ResponseEntity.ok(
        ApiResponse.ok(
            systemService.reopenEvaluation(evaluationId, actorId, request.getReason())));
  }

  /** Extract user ID from Spring Security authentication */
  private Long extractUserIdFromAuthentication(Authentication authentication) {
    if (authentication.getPrincipal()
        instanceof com.reviewflow.infrastructure.security.ReviewFlowUserDetails details) {
      return details.getUserId();
    }
    return null;
  }

  private static String clientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  // Request DTOs
  public static class ForceLogoutRequest {

    @jakarta.validation.constraints.NotBlank
    private String reason;

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }

  public static class UnlockTeamRequest {

    @jakarta.validation.constraints.NotBlank
    private String reason;

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }

  public static class ReopenEvaluationRequest {

    @jakarta.validation.constraints.NotBlank
    private String reason;

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }
}
