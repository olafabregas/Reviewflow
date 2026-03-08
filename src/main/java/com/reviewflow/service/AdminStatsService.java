package com.reviewflow.service;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.model.dto.response.AdminStatsDto;
import com.reviewflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository       userRepository;
    private final CourseRepository     courseRepository;
    private final AssignmentRepository assignmentRepository;
    private final TeamRepository       teamRepository;
    private final SubmissionRepository submissionRepository;

    @Cacheable(value = CacheConfig.CACHE_ADMIN_STATS, key = "'global'")
    @Transactional(readOnly = true)
    public AdminStatsDto getSystemStats() {
        long totalUsers           = userRepository.count();
        long totalStudents        = userRepository.countByRole(com.reviewflow.model.entity.UserRole.STUDENT);
        long totalInstructors     = userRepository.countByRole(com.reviewflow.model.entity.UserRole.INSTRUCTOR);
        long totalAdmins          = userRepository.countByRole(com.reviewflow.model.entity.UserRole.ADMIN);
        long totalCourses         = courseRepository.count();
        long activeCourses        = courseRepository.countByIsArchivedFalse();
        long archivedCourses      = courseRepository.countByIsArchivedTrue();
        long totalAssignments     = assignmentRepository.count();
        long publishedAssignments = assignmentRepository.countByIsPublishedTrue();
        long totalTeams           = teamRepository.count();
        long totalSubmissions     = submissionRepository.count();
        long storageBytes         = submissionRepository.sumFileSizeBytes();

        return AdminStatsDto.builder()
                .totalUsers(totalUsers)
                .usersByRole(AdminStatsDto.RoleBreakdown.builder()
                        .students(totalStudents)
                        .instructors(totalInstructors)
                        .admins(totalAdmins)
                        .build())
                .totalCourses(totalCourses)
                .activeCourses(activeCourses)
                .archivedCourses(archivedCourses)
                .totalAssignments(totalAssignments)
                .publishedAssignments(publishedAssignments)
                .totalTeams(totalTeams)
                .totalSubmissions(totalSubmissions)
                .storageUsedBytes(storageBytes)
                .storageUsedFormatted(formatBytes(storageBytes))
                .build();
    }

    // Called by other services after data-changing operations
    // Method body is intentionally empty — @CacheEvict does the work
    @CacheEvict(value = CacheConfig.CACHE_ADMIN_STATS, key = "'global'")
    public void evictStats() {}

    private String formatBytes(long bytes) {
        if (bytes < 1024)               return bytes + " B";
        if (bytes < 1024 * 1024)        return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB",  bytes / (1024.0 * 1024 * 1024));
    }
}
