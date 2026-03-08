package com.reviewflow.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    // Use these constants everywhere — never raw strings
    public static final String CACHE_ADMIN_STATS  = "adminStats";
    public static final String CACHE_UNREAD_COUNT = "unreadCount";
    public static final String CACHE_USER_COURSES = "userCourses";
    public static final String CACHE_ASSIGNMENT   = "assignmentDetail";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
            buildCache(CACHE_ADMIN_STATS, 60, 1),
            buildCache(CACHE_UNREAD_COUNT, 30, 500),
            buildCache(CACHE_USER_COURSES, 300, 500),
            buildCache(CACHE_ASSIGNMENT, 600, 200)
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
