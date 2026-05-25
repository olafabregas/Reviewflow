package com.reviewflow.discussion.service;

import com.reviewflow.shared.constant.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

@Component
public class DiscussionListCacheEviction {

  @Caching(
      evict = {
        @CacheEvict(
            value = CacheNames.CACHE_COURSE_DISCUSSIONS,
            key = "#courseId + ':STUDENT'"),
        @CacheEvict(
            value = CacheNames.CACHE_COURSE_DISCUSSIONS,
            key = "#courseId + ':INSTRUCTOR'"),
        @CacheEvict(value = CacheNames.CACHE_COURSE_DISCUSSIONS, key = "#courseId + ':ADMIN'"),
        @CacheEvict(
            value = CacheNames.CACHE_COURSE_DISCUSSIONS,
            key = "#courseId + ':SYSTEM_ADMIN'")
      })
  public void evictCourseDiscussionList(Long courseId) {}
}
