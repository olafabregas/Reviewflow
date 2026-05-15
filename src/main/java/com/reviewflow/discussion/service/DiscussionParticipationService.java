package com.reviewflow.discussion.service;

import com.reviewflow.discussion.repository.DiscussionPostRepository;
import com.reviewflow.shared.constant.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiscussionParticipationService {

  private final DiscussionPostRepository discussionPostRepository;

  @Cacheable(
      value = CacheNames.CACHE_DISCUSSION_PARTICIPATION,
      key = "#discussionId + ':' + #userId")
  public boolean hasStudentPosted(Long discussionId, Long userId) {
    return discussionPostRepository.countCountingInitialPosts(discussionId, userId) > 0;
  }

  @CacheEvict(value = CacheNames.CACHE_DISCUSSION_PARTICIPATION, key = "#discussionId + ':' + #userId")
  public void evictParticipation(Long discussionId, Long userId) {
    // no-op — eviction only
  }
}
