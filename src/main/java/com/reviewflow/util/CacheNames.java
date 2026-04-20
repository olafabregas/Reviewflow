package com.reviewflow.util;

/**
 * Cache name constants shared across services and event listeners.
 * Centralised here to avoid importing {@code CacheConfig} (a Spring
 * {@code @Configuration} class) from domain/service code.
 */
public final class CacheNames {

    private CacheNames() {}

    public static final String CACHE_ADMIN_STATS       = "adminStats";
    public static final String CACHE_UNREAD_COUNT      = "unreadCount";
    public static final String CACHE_USER_COURSES      = "userCourses";
    public static final String CACHE_ASSIGNMENT        = "assignmentDetail";
    public static final String CACHE_ASSIGNMENT_GROUPS = "courseGradeGroups";
    public static final String CACHE_COURSE_MODULES    = "courseModules";
    public static final String CACHE_GRADE_OVERVIEW    = "gradeOverview";
    public static final String CACHE_CLASS_STATISTICS  = "classStatistics";
    public static final String CACHE_CSV_IMPORTS       = "csvImports";
}
