package com.reviewflow.auth.config;

import com.reviewflow.auth.service.CaffeineTokenVersionStore;
import com.reviewflow.auth.service.RedisTokenVersionStore;
import com.reviewflow.auth.service.TokenVersionStore;
import com.reviewflow.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class TokenVersionStoreConfiguration {

  @Bean
  @Primary
  @ConditionalOnProperty(name = "auth.token-version.store", havingValue = "redis")
  public TokenVersionStore redisTokenVersionStore(
      StringRedisTemplate stringRedisTemplate,
      UserRepository userRepository,
      @Value("${session.token-version.redis-ttl-seconds:300}") int ttlSeconds) {
    return new RedisTokenVersionStore(stringRedisTemplate, userRepository, ttlSeconds);
  }

  @Bean
  @Primary
  @ConditionalOnProperty(
      name = "auth.token-version.store",
      havingValue = "caffeine",
      matchIfMissing = true)
  public TokenVersionStore caffeineTokenVersionStore(
      UserRepository userRepository,
      @Value("${session.token-version.cache-ttl-seconds:30}") int cacheTtlSeconds,
      @Value("${session.token-version.cache-max-size:50000}") int cacheMaxSize) {
    return new CaffeineTokenVersionStore(userRepository, cacheTtlSeconds, cacheMaxSize);
  }
}
