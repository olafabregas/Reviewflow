package com.reviewflow.auth.service;

import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.user.repository.UserRepository;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Shared token-version reads for multi-node deployments when {@code auth.token-version.store=redis}.
 */
@Slf4j
public class RedisTokenVersionStore implements TokenVersionStore {

  private static final String KEY_PREFIX = "rf:tv:";

  private final StringRedisTemplate redis;
  private final UserRepository userRepository;
  private final int ttlSeconds;

  public RedisTokenVersionStore(
      StringRedisTemplate redis,
      UserRepository userRepository,
      @Value("${session.token-version.redis-ttl-seconds:300}") int ttlSeconds) {
    this.redis = redis;
    this.userRepository = userRepository;
    this.ttlSeconds = ttlSeconds;
  }

  private static String key(Long userId) {
    return KEY_PREFIX + userId;
  }

  @Override
  public int getCurrentVersion(Long userId) {
    String cached = redis.opsForValue().get(key(userId));
    if (cached != null) {
      try {
        return Integer.parseInt(cached);
      } catch (NumberFormatException e) {
        log.warn("Invalid token version in Redis for userId={}", userId);
      }
    }
    int version =
        userRepository
            .findTokenVersionById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    redis.opsForValue().set(key(userId), Integer.toString(version), Duration.ofSeconds(ttlSeconds));
    return version;
  }

  @Override
  public void evict(Long userId) {
    redis.delete(key(userId));
  }
}
