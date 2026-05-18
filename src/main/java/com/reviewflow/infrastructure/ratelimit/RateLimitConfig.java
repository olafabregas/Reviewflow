package com.reviewflow.infrastructure.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

  @Value("${spring.data.redis.url:}")
  private String redisUrl;

  @Value("${spring.data.redis.host:localhost}")
  private String redisHost;

  @Value("${spring.data.redis.port:6379}")
  private int redisPort;

  @Value("${spring.data.redis.password:}")
  private String redisPassword;

  @Value("${spring.data.redis.ssl.enabled:false}")
  private boolean redisSsl;

  @Bean(destroyMethod = "shutdown")
  public RedisClient rateLimitRedisClient() {
    return RedisClient.create(buildRedisUri());
  }

  @Bean(destroyMethod = "close")
  public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(
      RedisClient rateLimitRedisClient) {
    return rateLimitRedisClient.connect(
        RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
  }

  @Bean
  public ProxyManager<String> rateLimitProxyManager(
      StatefulRedisConnection<String, byte[]> rateLimitRedisConnection) {
    return LettuceBasedProxyManager.builderFor(rateLimitRedisConnection)
        .withExpirationStrategy(
            ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                Duration.ofSeconds(10)))
        .build();
  }

  private RedisURI buildRedisUri() {
    if (redisUrl != null && !redisUrl.isBlank()) {
      return RedisURI.create(redisUrl);
    }
    RedisURI.Builder builder = RedisURI.builder().withHost(redisHost).withPort(redisPort);
    if (redisPassword != null && !redisPassword.isBlank()) {
      builder.withPassword(redisPassword.toCharArray());
    }
    if (redisSsl) {
      builder.withSsl(true);
    }
    return builder.build();
  }
}
