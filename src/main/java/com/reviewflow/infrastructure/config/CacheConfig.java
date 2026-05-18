package com.reviewflow.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.reviewflow.shared.constant.CacheNames;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

  // Delegate to CacheNames so service/event code can import CacheNames
  // without pulling in this @Configuration class and its transitive deps.
  public static final String CACHE_ADMIN_STATS = CacheNames.CACHE_ADMIN_STATS;
  public static final String CACHE_UNREAD_COUNT = CacheNames.CACHE_UNREAD_COUNT;
  public static final String CACHE_USER_COURSES = CacheNames.CACHE_USER_COURSES;
  public static final String CACHE_ASSIGNMENT = CacheNames.CACHE_ASSIGNMENT;
  public static final String CACHE_ASSIGNMENT_GROUPS = CacheNames.CACHE_ASSIGNMENT_GROUPS;
  public static final String CACHE_COURSE_MODULES = CacheNames.CACHE_COURSE_MODULES;
  public static final String CACHE_GRADE_OVERVIEW = CacheNames.CACHE_GRADE_OVERVIEW;
  public static final String CACHE_CLASS_STATISTICS = CacheNames.CACHE_CLASS_STATISTICS;

  @Value("${cache.grade-overview.ttl-seconds:300}")
  private int gradeOverviewTtlSeconds;

  @Value("${cache.grade-overview.max-size:5000}")
  private int gradeOverviewMaxSize;

  @Value("${cache.class-statistics.ttl-seconds:180}")
  private int classStatisticsTtlSeconds;

  @Value("${cache.class-statistics.max-size:300}")
  private int classStatisticsMaxSize;

  @Value("${cache.discussion-participation.ttl-seconds:1800}")
  private int discussionParticipationTtlSeconds;

  @Value("${cache.discussion-participation.max-size:50000}")
  private int discussionParticipationMaxSize;

  @Value("${cache.course-discussions.ttl-seconds:300}")
  private int courseDiscussionsTtlSeconds;

  @Value("${cache.course-discussions.max-size:500}")
  private int courseDiscussionsMaxSize;

  @Bean
  public CacheManager cacheManager() {
    SimpleCacheManager cacheManager = new SimpleCacheManager();
    cacheManager.setCaches(
        Arrays.asList(
            buildCache(CACHE_ADMIN_STATS, 60, 1),
            buildCache(CACHE_UNREAD_COUNT, 30, 500),
            buildCache(CACHE_USER_COURSES, 300, 500),
            buildCache(CACHE_ASSIGNMENT, 600, 200),
            buildCache(CACHE_ASSIGNMENT_GROUPS, 300, 200),
            buildCache(CACHE_COURSE_MODULES, 300, 200),
            buildCache(CACHE_GRADE_OVERVIEW, gradeOverviewTtlSeconds, gradeOverviewMaxSize),
            buildCache(CACHE_CLASS_STATISTICS, classStatisticsTtlSeconds, classStatisticsMaxSize),
            buildCache(CacheNames.CACHE_DISCUSSION_PARTICIPATION, discussionParticipationTtlSeconds, discussionParticipationMaxSize),
            buildCache(CacheNames.CACHE_COURSE_DISCUSSIONS, courseDiscussionsTtlSeconds, courseDiscussionsMaxSize)));
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
        false);
  }
}
