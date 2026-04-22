package com.reviewflow.controller;

import java.util.List;
import java.util.Map;

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

import com.reviewflow.dto.CacheEvictResponse;
import com.reviewflow.dto.CacheStatsDto;
import com.reviewflow.dto.ForceLogoutResponse;
import com.reviewflow.dto.ReopenEvaluationResponse;
import com.reviewflow.dto.SecurityEventDto;
import com.reviewflow.dto.UnlockTeamResponse;
import com.reviewflow.service.SystemService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "System Admin", description = "PRD-09: Platform operator endpoints - SYSTEM_ADMIN role only")
public class SystemController {

    private final SystemService systemService;

    /**
     * PRD-09 Flow B: Get cache statistics
     */
    @GetMapping("/cache/stats")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get cache statistics", description = "Returns hit/miss stats for all 5 caches (adminStats, unreadCount, userCourses, assignmentDetail, courseGradeGroups)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cache statistics retrieved successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - SYSTEM_ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    public ResponseEntity<List<CacheStatsDto>> getCacheStats() {
        List<CacheStatsDto> stats = systemService.getCacheStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * PRD-09 Flow B: Evict a cache with 60-second throttle protection
     */
    @PostMapping("/cache/evict/{cacheName}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Evict cache by name", description = "Clears all entries from named cache. Throttled: max 1 eviction per 60 seconds per cache.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cache evicted successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid cache name or throttle limit exceeded",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - SYSTEM_ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    public ResponseEntity<CacheEvictResponse> evictCache(
            @PathVariable String cacheName,
            Authentication authentication) {

        Long actorId = extractUserIdFromAuthentication(authentication);
        CacheEvictResponse result = systemService.evictCache(cacheName, actorId);
        return ResponseEntity.ok(result);
    }

    /**
     * PRD-09 Flow C: Get safe (non-secret) configuration
     */
    @GetMapping("/config")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get safe configuration", description = "Returns whitelisted configuration properties only (no secrets)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Configuration retrieved successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - SYSTEM_ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    public ResponseEntity<Map<String, String>> getSafeConfig() {
        Map<String, String> config = systemService.getSafeConfig();
        return ResponseEntity.ok(config);
    }

    /**
     * PRD-09 Flow D: Get security events
     */
    @GetMapping("/security/events")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get security events", description = "Returns recent security events (failed logins, rate limits, blocked uploads)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Security events retrieved successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - SYSTEM_ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    public ResponseEntity<List<SecurityEventDto>> getSecurityEvents(
            @RequestParam(defaultValue = "50") int limit) {

        List<SecurityEventDto> events = systemService.getSecurityEvents(Math.min(limit, 500));
        return ResponseEntity.ok(events);
    }

    /**
     * PRD-09 Flow E: Force logout a user
     */
    @PostMapping("/users/{targetUserId}/force-logout")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Force logout a user", description = "Revoke all tokens for target user and close their sessions")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "User forced logout successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid user ID or reason",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - SYSTEM_ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - user does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    public ResponseEntity<ForceLogoutResponse> forceLogout(
            @PathVariable String targetUserId,
            @RequestBody ForceLogoutRequest request,
            Authentication authentication) {

        Long actorId = extractUserIdFromAuthentication(authentication);
        ForceLogoutResponse result = systemService.forceLogout(targetUserId, actorId, request.getReason());
        return ResponseEntity.ok(result);
    }

    /**
     * PRD-09 Flow F: Unlock a team (system override)
     */
    @PostMapping("/teams/{teamId}/unlock")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Unlock a team", description = "System override - unlocks a locked team")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Team unlocked successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid team ID or reason",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - SYSTEM_ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - team does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    public ResponseEntity<UnlockTeamResponse> unlockTeam(
            @PathVariable String teamId,
            @RequestBody UnlockTeamRequest request,
            Authentication authentication) {

        Long actorId = extractUserIdFromAuthentication(authentication);
        UnlockTeamResponse result = systemService.unlockTeam(teamId, actorId, request.getReason());
        return ResponseEntity.ok(result);
    }

    /**
     * PRD-09 Flow G: Reopen an evaluation (system override)
     */
    @PostMapping("/evaluations/{evaluationId}/reopen")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Reopen an evaluation", description = "System override - reopens a published evaluation for instructor edits")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Evaluation reopened successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid evaluation ID or reason",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - SYSTEM_ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - evaluation does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    public ResponseEntity<ReopenEvaluationResponse> reopenEvaluation(
            @PathVariable String evaluationId,
            @RequestBody ReopenEvaluationRequest request,
            Authentication authentication) {

        Long actorId = extractUserIdFromAuthentication(authentication);
        ReopenEvaluationResponse result = systemService.reopenEvaluation(evaluationId, actorId, request.getReason());
        return ResponseEntity.ok(result);
    }

    /**
     * Extract user ID from Spring Security authentication
     */
    private Long extractUserIdFromAuthentication(Authentication authentication) {
        if (authentication.getPrincipal() instanceof com.reviewflow.security.ReviewFlowUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    // Request DTOs
    public static class ForceLogoutRequest {

        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class UnlockTeamRequest {

        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class ReopenEvaluationRequest {

        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
