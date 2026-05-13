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
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class TokenVersionStoreConfiguration {

  @Bean
  @Primary
  @ConditionalOnProperty(name = "auth.token-version.store", havingValue = "redis")
  public RedisTokenVersionStore redisTokenVersionStore(
      StringRedisTemplate stringRedisTemplate,
      UserRepository userRepository,
      @Value("${session.token-version.redis-ttl-seconds:300}") int ttlSeconds,
      @Value("${session.token-version.cache-ttl-seconds:30}") int localCacheTtlSeconds,
      @Value("${session.token-version.cache-max-size:50000}") int localCacheMaxSize,
      @Value("${session.token-version.invalidation-channel:token-version-invalidations}")
          String invalidationChannel) {
    return new RedisTokenVersionStore(
        stringRedisTemplate,
        userRepository,
        ttlSeconds,
        localCacheTtlSeconds,
        localCacheMaxSize,
        invalidationChannel);
  }

  @Bean
  @ConditionalOnProperty(name = "auth.token-version.store", havingValue = "redis")
  public ChannelTopic tokenVersionInvalidationTopic(
      @Value("${session.token-version.invalidation-channel:token-version-invalidations}")
          String invalidationChannel) {
    return new ChannelTopic(invalidationChannel);
  }

  @Bean
  @ConditionalOnProperty(name = "auth.token-version.store", havingValue = "redis")
  public RedisMessageListenerContainer tokenVersionMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      RedisTokenVersionStore redisTokenVersionStore,
      ChannelTopic tokenVersionInvalidationTopic) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(redisTokenVersionStore, tokenVersionInvalidationTopic);
    return container;
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
