package com.reviewflow.auth.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.user.repository.UserRepository;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;

/**
 * Per-node token version cache (default {@link TokenVersionStore} implementation).
 */
public class CaffeineTokenVersionStore implements TokenVersionStore {

  private final LoadingCache<Long, Integer> cache;

  public CaffeineTokenVersionStore(
      UserRepository userRepository,
      @Value("${session.token-version.cache-ttl-seconds:30}") int cacheTtlSeconds,
      @Value("${session.token-version.cache-max-size:50000}") int cacheMaxSize) {
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
            .maximumSize(cacheMaxSize)
            .build(
                userId ->
                    userRepository
                        .findTokenVersionById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", userId)));
  }

  @Override
  public int getCurrentVersion(Long userId) {
    return cache.get(userId);
  }

  @Override
  public void evict(Long userId) {
    cache.invalidate(userId);
  }
}
