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
import com.reviewflow.util.CacheNames;

@Configuration
public class CacheConfig {

    // Delegate to CacheNames so service/event code can import CacheNames
    // without pulling in this @Configuration class and its transitive deps.
    public static final String CACHE_ADMIN_STATS       = CacheNames.CACHE_ADMIN_STATS;
    public static final String CACHE_UNREAD_COUNT      = CacheNames.CACHE_UNREAD_COUNT;
    public static final String CACHE_USER_COURSES      = CacheNames.CACHE_USER_COURSES;
    public static final String CACHE_ASSIGNMENT        = CacheNames.CACHE_ASSIGNMENT;
    public static final String CACHE_ASSIGNMENT_GROUPS = CacheNames.CACHE_ASSIGNMENT_GROUPS;
    public static final String CACHE_COURSE_MODULES    = CacheNames.CACHE_COURSE_MODULES;
    public static final String CACHE_GRADE_OVERVIEW    = CacheNames.CACHE_GRADE_OVERVIEW;
    public static final String CACHE_CLASS_STATISTICS  = CacheNames.CACHE_CLASS_STATISTICS;
    public static final String CACHE_CSV_IMPORTS       = CacheNames.CACHE_CSV_IMPORTS;

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
