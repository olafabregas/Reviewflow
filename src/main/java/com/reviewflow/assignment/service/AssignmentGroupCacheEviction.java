package com.reviewflow.assignment.service;

import com.reviewflow.shared.constant.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

@Component
public class AssignmentGroupCacheEviction {

  @Caching(
      evict = {
        @CacheEvict(value = CacheNames.CACHE_ASSIGNMENT_GROUPS, key = "#courseId"),
        @CacheEvict(value = CacheNames.CACHE_GRADE_OVERVIEW, allEntries = true),
        @CacheEvict(value = CacheNames.CACHE_CLASS_STATISTICS, key = "#courseId")
      })
  public void evictForCourse(Long courseId) {}

  @CacheEvict(value = CacheNames.CACHE_ASSIGNMENT, key = "#assignmentId")
  public void evictAssignment(Long assignmentId) {}
}
