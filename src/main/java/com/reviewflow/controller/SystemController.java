package com.reviewflow.controller;

import com.reviewflow.dto.CacheStatsDto;
import com.reviewflow.dto.SystemMetricsDto;
import com.reviewflow.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @Operation(summary = "Get cache statistics", description = "Returns hit/miss stats for all 4 caches (adminStats, unreadCount, userCourses, assignmentDetail)")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        List<CacheStatsDto> stats = systemService.getCacheStats();

        Map<String, Object> response = new HashMap<>();
        response.put("caches", stats);
        response.put("timestamp", java.time.Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * PRD-09 Flow B: Evict a cache with 60-second throttle protection
     */
    @PostMapping("/cache/evict/{cacheName}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Evict cache by name", description = "Clears all entries from named cache. Throttled: max 1 eviction per 60 seconds per cache.")
    public ResponseEntity<Map<String, Object>> evictCache(
            @PathVariable String cacheName,
            Authentication authentication) {

        Long actorId = extractUserIdFromAuthentication(authentication);
        Map<String, Object> result = systemService.evictCache(cacheName, actorId);

        return ResponseEntity.ok(result);
    }

    /**
     * PRD-09 Flow C: Get safe (non-secret) configuration
     */
    @GetMapping("/config")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get safe configuration", description = "Returns whitelisted configuration properties only (no secrets)")
    public ResponseEntity<Map<String, Object>> getSafeConfig() {
        Map<String, String> config = systemService.getSafeConfig();

        Map<String, Object> response = new HashMap<>();
        response.put("config", config);
        response.put("timestamp", java.time.Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * PRD-09 Flow D: Get security events
     */
    @GetMapping("/security/events")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get security events", description = "Returns recent security events (failed logins, rate limits, blocked uploads)")
    public ResponseEntity<Map<String, Object>> getSecurityEvents(
            @RequestParam(defaultValue = "50") int limit) {

        List<Map<String, Object>> events = systemService.getSecurityEvents(Math.min(limit, 500));

        Map<String, Object> response = new HashMap<>();
        response.put("events", events);
        response.put("limit", limit);
        response.put("count", events.size());
        response.put("timestamp", java.time.Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * PRD-09 Flow E: Force logout a user
     */
    @PostMapping("/users/{targetUserId}/force-logout")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Force logout a user", description = "Revoke all tokens for target user and close their sessions")
    public ResponseEntity<Map<String, Object>> forceLogout(
            @PathVariable String targetUserId,
            @RequestBody ForceLogoutRequest request,
            Authentication authentication) {

        Long actorId = extractUserIdFromAuthentication(authentication);
        Map<String, Object> result = systemService.forceLogout(targetUserId, actorId, request.getReason());

        return ResponseEntity.ok(result);
    }

    /**
     * PRD-09 Flow F: Unlock a team (system override)
     */
    @PostMapping("/teams/{teamId}/unlock")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Unlock a team", description = "System override - unlocks a locked team")
    public ResponseEntity<Map<String, Object>> unlockTeam(
            @PathVariable String teamId,
            @RequestBody UnlockTeamRequest request,
            Authentication authentication) {

        Long actorId = extractUserIdFromAuthentication(authentication);
        Map<String, Object> result = systemService.unlockTeam(teamId, actorId, request.getReason());

        return ResponseEntity.ok(result);
    }

    /**
     * PRD-09 Flow G: Reopen an evaluation (system override)
     */
    @PostMapping("/evaluations/{evaluationId}/reopen")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Reopen an evaluation", description = "System override - reopens a published evaluation for instructor edits")
    public ResponseEntity<Map<String, Object>> reopenEvaluation(
            @PathVariable String evaluationId,
            @RequestBody ReopenEvaluationRequest request,
            Authentication authentication) {

        Long actorId = extractUserIdFromAuthentication(authentication);
        Map<String, Object> result = systemService.reopenEvaluation(evaluationId, actorId, request.getReason());

        return ResponseEntity.ok(result);
    }

    /**
     * Extract user ID from Spring Security authentication
     */
    private Long extractUserIdFromAuthentication(Authentication authentication) {
        // Extract from JWT or security context
        // This depends on your ReviewFlowUserDetails implementation
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.reviewflow.security.ReviewFlowUserDetails) {
            return ((com.reviewflow.security.ReviewFlowUserDetails) principal).getUserId();
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
