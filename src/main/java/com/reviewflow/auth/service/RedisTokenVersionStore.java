package com.reviewflow.auth.service;

import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.user.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Shared token-version reads for multi-node deployments when {@code auth.token-version.store=redis}.
 */
@Slf4j
public class RedisTokenVersionStore implements TokenVersionStore, MessageListener {

  private static final String KEY_PREFIX = "rf:tv:";

  private final StringRedisTemplate redis;
  private final UserRepository userRepository;
  private final int ttlSeconds;
  private final String invalidationChannel;
  private final LoadingCache<Long, Integer> localCache;

  public RedisTokenVersionStore(
      StringRedisTemplate redis,
      UserRepository userRepository,
      @Value("${session.token-version.redis-ttl-seconds:300}") int ttlSeconds,
      @Value("${session.token-version.cache-ttl-seconds:30}") int localCacheTtlSeconds,
      @Value("${session.token-version.cache-max-size:50000}") int localCacheMaxSize,
      @Value("${session.token-version.invalidation-channel:token-version-invalidations}")
          String invalidationChannel) {
    this.redis = redis;
    this.userRepository = userRepository;
    this.ttlSeconds = ttlSeconds;
    this.invalidationChannel = invalidationChannel;
    this.localCache =
        Caffeine.newBuilder()
            .expireAfterWrite(localCacheTtlSeconds, TimeUnit.SECONDS)
            .maximumSize(localCacheMaxSize)
            .build(this::readVersionFromSharedStore);
  }

  private static String key(Long userId) {
    return KEY_PREFIX + userId;
  }

  @Override
  public int getCurrentVersion(Long userId) {
    return localCache.get(userId);
  }

  private int readVersionFromSharedStore(Long userId) {
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
  public void invalidate(Long userId) {
    int freshVersion =
        userRepository
            .findTokenVersionById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    redis.opsForValue().set(key(userId), Integer.toString(freshVersion), Duration.ofSeconds(ttlSeconds));
    localCache.invalidate(userId);
    redis.convertAndSend(invalidationChannel, userId.toString());
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String payload = null;
    try {
      payload = new String(message.getBody(), StandardCharsets.UTF_8).trim();
      Long userId = Long.parseLong(payload);
      localCache.invalidate(userId);
    } catch (Exception e) {
      log.warn("Ignoring malformed token-version invalidation payload: {}", payload, e);
    }
  }
}
