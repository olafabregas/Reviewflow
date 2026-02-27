package com.reviewflow.controller;

import com.reviewflow.model.entity.UserRole;
import com.reviewflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final SubmissionRepository submissionRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> stats() {
        long totalUsers = userRepository.count();
        long students = userRepository.countByRole(UserRole.STUDENT);
        long instructors = userRepository.countByRole(UserRole.INSTRUCTOR);
        long admins = userRepository.countByRole(UserRole.ADMIN);
        long totalCourses = courseRepository.count();
        long totalSubmissions = submissionRepository.count();
        long storageUsed = submissionRepository.sumFileSizeBytes() != null ? submissionRepository.sumFileSizeBytes() : 0L;
        long activeCourses = courseRepository.countByIsArchivedFalse();

        return ResponseEntity.ok(Map.of(
                "totalUsers", totalUsers,
                "usersByRole", Map.of("STUDENT", students, "INSTRUCTOR", instructors, "ADMIN", admins),
                "totalCourses", totalCourses,
                "activeCourses", activeCourses,
                "totalSubmissions", totalSubmissions,
                "storageUsedBytes", storageUsed
        ));
    }
}
