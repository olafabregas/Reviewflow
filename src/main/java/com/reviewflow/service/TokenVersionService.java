package com.reviewflow.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.user.repository.UserRepository;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Caches the current token version for each user using a Caffeine {@link LoadingCache}. On cache
 * miss, a single primary-key DB read repopulates the entry. Concurrent requests for the same userId
 * collapse to one DB read (LoadingCache guarantee).
 *
 * <p>This is a standalone cache, intentionally NOT registered with Spring's {@link
 * org.springframework.cache.CacheManager}. It is an infrastructure concern for the auth hot path,
 * not a business-level cache.
 */
@Service
public class TokenVersionService {

  private final LoadingCache<Long, Integer> cache;

  public TokenVersionService(
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

  /** Returns the current token version for the given user. Cache hit is sub-microsecond. */
  public int getCurrentVersion(Long userId) {
    return cache.get(userId);
  }

  /**
   * Evicts the cached token version for the given user. Best-effort — if this call fails or is
   * skipped, the cache TTL (default 30 seconds) ensures eventual consistency.
   */
  public void evict(Long userId) {
    cache.invalidate(userId);
  }
}
