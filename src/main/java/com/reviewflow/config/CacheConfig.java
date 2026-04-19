package com.reviewflow.config;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class CacheConfig {

    // Use these constants everywhere — never raw strings
    public static final String CACHE_ADMIN_STATS = "adminStats";
    public static final String CACHE_UNREAD_COUNT = "unreadCount";
    public static final String CACHE_USER_COURSES = "userCourses";
    public static final String CACHE_ASSIGNMENT = "assignmentDetail";
    public static final String CACHE_ASSIGNMENT_GROUPS = "courseGradeGroups";
    public static final String CACHE_COURSE_MODULES = "courseModules";
    public static final String CACHE_GRADE_OVERVIEW = "gradeOverview";
    public static final String CACHE_CLASS_STATISTICS = "classStatistics";
    public static final String CACHE_CSV_IMPORTS = "csvImports";

    @Value("${cache.grade-overview.ttl-seconds:300}")
    private int gradeOverviewTtlSeconds;

    @Value("${cache.grade-overview.max-size:5000}")
    private int gradeOverviewMaxSize;

    @Value("${cache.class-statistics.ttl-seconds:180}")
    private int classStatisticsTtlSeconds;

    @Value("${cache.class-statistics.max-size:300}")
    private int classStatisticsMaxSize;

    @Value("${cache.csv-imports.ttl-seconds:600}")
    private int csvImportsTtlSeconds;

    @Value("${cache.csv-imports.max-size:50}")
    private int csvImportsMaxSize;

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
                buildCache(CACHE_ADMIN_STATS, 60, 1),
                buildCache(CACHE_UNREAD_COUNT, 30, 500),
                buildCache(CACHE_USER_COURSES, 300, 500),
                buildCache(CACHE_ASSIGNMENT, 600, 200),
                buildCache(CACHE_ASSIGNMENT_GROUPS, 300, 200),
                buildCache(CACHE_COURSE_MODULES, 300, 200),
                buildCache(CACHE_GRADE_OVERVIEW, gradeOverviewTtlSeconds, gradeOverviewMaxSize),
                buildCache(CACHE_CLASS_STATISTICS, classStatisticsTtlSeconds, classStatisticsMaxSize),
                buildCache(CACHE_CSV_IMPORTS, csvImportsTtlSeconds, csvImportsMaxSize)
        ));
        return cacheManager;
    }

    private Cache buildCache(String name, int ttlSeconds, int maxSize) {
        return new org.springframework.cache.concurrent.ConcurrentMapCache(
                name,
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                        .recordStats()
                        .<Object, Object>build()
                        .asMap(),
                false
        );
    }
}
