package com.reviewflow.controller;

import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseRepository;
import com.reviewflow.repository.SubmissionRepository;
import com.reviewflow.repository.TeamRepository;
import com.reviewflow.repository.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
@Tag(name = "Admin Statistics", description = "System-wide statistics and analytics for administrators")
public class AdminStatsController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final AssignmentRepository assignmentRepository;
    private final TeamRepository teamRepository;
    private final SubmissionRepository submissionRepository;

    @Operation(
            summary = "Get admin statistics",
            description = "Get comprehensive system statistics including user counts by role, course counts, "
            + "assignment statistics, team counts, submission counts, and storage usage. "
            + "Results are cached for performance. Admin-only endpoint."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Statistics retrieved successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping
    @Cacheable(value = "adminStats", key = "'stats'")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        long totalUsers = userRepository.count();
        long students = userRepository.countByRole(UserRole.STUDENT);
        long instructors = userRepository.countByRole(UserRole.INSTRUCTOR);
        long admins = userRepository.countByRole(UserRole.ADMIN);

        long totalCourses = courseRepository.count();
        long activeCourses = courseRepository.countByIsArchivedFalse();
        long archivedCourses = courseRepository.countByIsArchivedTrue();

        long totalAssignments = assignmentRepository.count();
        long publishedAssignments = assignmentRepository.countByIsPublishedTrue();

        long totalTeams = teamRepository.count();
        long totalSubmissions = submissionRepository.count();

        Long storageSum = submissionRepository.sumFileSizeBytes();
        long storageUsedBytes = storageSum != null ? storageSum : 0L;
        String storageUsedFormatted = formatBytes(storageUsedBytes);

        Map<String, Object> data = Map.ofEntries(
                Map.entry("totalUsers", totalUsers),
                Map.entry("usersByRole", Map.of("STUDENT", students, "INSTRUCTOR", instructors, "ADMIN", admins)),
                Map.entry("totalCourses", totalCourses),
                Map.entry("activeCourses", activeCourses),
                Map.entry("archivedCourses", archivedCourses),
                Map.entry("totalAssignments", totalAssignments),
                Map.entry("publishedAssignments", publishedAssignments),
                Map.entry("totalTeams", totalTeams),
                Map.entry("totalSubmissions", totalSubmissions),
                Map.entry("storageUsedBytes", storageUsedBytes),
                Map.entry("storageUsedFormatted", storageUsedFormatted)
        );

        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.2f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.2f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }
}
