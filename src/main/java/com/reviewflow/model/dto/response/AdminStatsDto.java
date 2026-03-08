package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminStatsDto {
    long          totalUsers;
    RoleBreakdown usersByRole;
    long          totalCourses;
    long          activeCourses;
    long          archivedCourses;
    long          totalAssignments;
    long          publishedAssignments;
    long          totalTeams;
    long          totalSubmissions;
    long          storageUsedBytes;
    String        storageUsedFormatted;

    @Value
    @Builder
    public static class RoleBreakdown {
        long students;
        long instructors;
        long admins;
    }
}
