package com.reviewflow.grading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewflow.grading.dto.response.GradeOverviewDto;
import com.reviewflow.shared.constant.CacheNames;
import com.reviewflow.shared.exception.StorageException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GradeAggregateService {

  private static final String AGG_KEY_PREFIX = "reviewflow:grade:aggregate:";
  private static final String STATS_KEY_PREFIX = "reviewflow:grade:stats:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final CacheManager cacheManager;

  @Value("${grade.aggregate.ttl-minutes:5}")
  private int ttlMinutes;

  public Optional<GradeOverviewDto> getFromRedis(Long courseId, Long studentId) {
    String value = redisTemplate.opsForValue().get(aggKey(courseId, studentId));
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(value, GradeOverviewDto.class));
    } catch (JsonProcessingException e) {
      return Optional.empty();
    }
  }

  public void storeInRedis(Long courseId, Long studentId, GradeOverviewDto overview) {
    try {
      redisTemplate
          .opsForValue()
          .set(
              aggKey(courseId, studentId),
              objectMapper.writeValueAsString(overview),
              Duration.ofMinutes(ttlMinutes));
    } catch (JsonProcessingException e) {
      throw new StorageException("Failed to store grade aggregate", e);
    }
  }

  public void evictStudent(Long courseId, Long studentId) {
    Cache overview = cacheManager.getCache(CacheNames.CACHE_GRADE_OVERVIEW);
    if (overview != null) {
      overview.evict(courseId + ":" + studentId);
    }
    redisTemplate.delete(aggKey(courseId, studentId));
  }

  public void evictCourse(Long courseId, List<Long> enrolledStudentIds) {
    Cache overview = cacheManager.getCache(CacheNames.CACHE_GRADE_OVERVIEW);
    Cache classStats = cacheManager.getCache(CacheNames.CACHE_CLASS_STATISTICS);
    for (Long studentId : enrolledStudentIds) {
      if (overview != null) {
        overview.evict(courseId + ":" + studentId);
      }
      redisTemplate.delete(aggKey(courseId, studentId));
    }
    redisTemplate.delete(STATS_KEY_PREFIX + courseId);
    if (classStats != null) {
      classStats.evict(courseId);
    }
  }

  private static String aggKey(Long courseId, Long studentId) {
    return AGG_KEY_PREFIX + courseId + ":" + studentId;
  }
}
