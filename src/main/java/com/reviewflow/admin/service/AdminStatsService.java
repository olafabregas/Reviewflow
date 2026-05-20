package com.reviewflow.admin.service;

import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.course.repository.CourseRepository;
import com.reviewflow.shared.constant.CacheNames;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.submission.repository.SubmissionRepository;
import com.reviewflow.team.repository.TeamRepository;
import com.reviewflow.user.repository.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

  private final UserRepository userRepository;
  private final CourseRepository courseRepository;
  private final AssignmentRepository assignmentRepository;
  private final TeamRepository teamRepository;
  private final SubmissionRepository submissionRepository;

  @Cacheable(value = CacheNames.CACHE_ADMIN_STATS, key = "'stats'")
  public Map<String, Object> getStats() {
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

    return Map.ofEntries(
        Map.entry("totalUsers", totalUsers),
        Map.entry(
            "usersByRole",
            Map.of("STUDENT", students, "INSTRUCTOR", instructors, "ADMIN", admins)),
        Map.entry("totalCourses", totalCourses),
        Map.entry("activeCourses", activeCourses),
        Map.entry("archivedCourses", archivedCourses),
        Map.entry("totalAssignments", totalAssignments),
        Map.entry("publishedAssignments", publishedAssignments),
        Map.entry("totalTeams", totalTeams),
        Map.entry("totalSubmissions", totalSubmissions),
        Map.entry("storageUsedBytes", storageUsedBytes),
        Map.entry("storageUsedFormatted", formatBytes(storageUsedBytes)));
  }

  @CacheEvict(value = CacheNames.CACHE_ADMIN_STATS, key = "'global'")
  public void evictStats() {}

  private static String formatBytes(long bytes) {
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
