package com.reviewflow.infrastructure.ratelimit;

import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.shared.domain.UserRole;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultRateLimitService implements RateLimitService {

  private final ProxyManager<String> rateLimitProxyManager;
  private final RateLimitConfigurationProvider configProvider;
  private final ReviewFlowMetrics metrics;

  @Override
  public RateLimitResult probe(String key, RateLimitStrategy strategy, UserRole role) {
    return execute(key, strategy, role, Operation.PROBE);
  }

  @Override
  public RateLimitResult tryConsume(String key, RateLimitStrategy strategy, UserRole role) {
    return execute(key, strategy, role, Operation.CONSUME);
  }

  @Override
  public RateLimitResult consumeOnFailure(String key, RateLimitStrategy strategy, UserRole role) {
    return tryConsume(key, strategy, role);
  }

  @Override
  public void reset(String key, RateLimitStrategy strategy) {
    if (key == null || key.isBlank()) {
      return;
    }
    try {
      rateLimitProxyManager.removeProxy(buildRedisKey(key, strategy));
    } catch (Exception e) {
      log.warn(
          "Rate limit reset failed (ignored): strategy={} key={}: {}",
          strategy,
          key,
          e.getMessage());
    }
  }

  private RateLimitResult execute(
      String key, RateLimitStrategy strategy, UserRole role, Operation operation) {
    if (key == null || key.isBlank()) {
      return RateLimitResult.allowedFailOpen(strategy);
    }
    String fullKey = buildRedisKey(key, strategy);
    long limit = configProvider.limitCapacity(strategy, role);
    try {
      BucketConfiguration config = configProvider.getConfig(strategy, role);
      Bucket bucket = rateLimitProxyManager.builder().build(fullKey, config);

      if (operation == Operation.PROBE) {
        EstimationProbe estimation = bucket.estimateAbilityToConsume(1);
        if (!estimation.canBeConsumed()) {
          return denied(strategy, role, estimation.getNanosToWaitForRefill(), limit);
        }
        return RateLimitResult.allowed(strategy, estimation.getRemainingTokens(), limit);
      }

      ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
      if (probe.isConsumed()) {
        return RateLimitResult.allowed(strategy, probe.getRemainingTokens(), limit);
      }
      return denied(strategy, role, probe.getNanosToWaitForRefill(), limit);
    } catch (Exception e) {
      log.warn(
          "Rate limit check failed (fail-open): strategy={} key={}: {}",
          strategy,
          fullKey,
          e.getMessage());
      metrics.recordRateLimitCheckFailed(strategy.name());
      return RateLimitResult.allowedFailOpen(strategy);
    }
  }

  private RateLimitResult denied(
      RateLimitStrategy strategy, UserRole role, long nanosToWait, long limit) {
    long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanosToWait));
    long resetEpoch = Instant.now().getEpochSecond() + retryAfterSeconds;
    metrics.recordRateLimitHit(strategy.name(), role);
    log.warn("Rate limit exceeded: strategy={}", strategy);
    return RateLimitResult.denied(strategy, retryAfterSeconds, limit, resetEpoch);
  }

  private String buildRedisKey(String identifier, RateLimitStrategy strategy) {
    return switch (strategy) {
      case AUTH_LOGIN -> "reviewflow:ratelimit:auth:login:" + identifier;
      case AUTH_REFRESH_IP -> "reviewflow:ratelimit:auth:refresh:ip:" + identifier;
      case AUTH_REFRESH_USER -> "reviewflow:ratelimit:auth:refresh:user:" + identifier;
      case AUTH_PASSWORD_RESET_REQUEST_IP ->
          "reviewflow:ratelimit:auth:pwreset:req:ip:" + identifier;
      case AUTH_PASSWORD_RESET_EMAIL -> "reviewflow:ratelimit:auth:pwreset:email:" + identifier;
      case AUTH_PASSWORD_RESET_CONFIRM_IP ->
          "reviewflow:ratelimit:auth:pwreset:confirm:" + identifier;
      case AUTH_STEP_UP -> "reviewflow:ratelimit:auth:stepup:" + identifier;
      case AUTH_JWT_FAILURE -> "reviewflow:ratelimit:auth:jwt:" + identifier;
      case AUTH_WS_TICKET -> "reviewflow:ratelimit:auth:wsticket:" + identifier;
      case MSG_SEND -> "reviewflow:ratelimit:msg:send:" + identifier;
      case MSG_CREATE -> "reviewflow:ratelimit:msg:create:" + identifier;
      case UPLOAD_BLOCK -> "reviewflow:ratelimit:upload:" + identifier;
      case API_PUBLIC -> "reviewflow:ratelimit:public:" + identifier;
      case API_READ -> "reviewflow:ratelimit:api:read:" + identifier;
      case API_WRITE -> "reviewflow:ratelimit:api:write:" + identifier;
      case API_EXPORT -> "reviewflow:ratelimit:api:export:" + identifier;
    };
  }

  private enum Operation {
    PROBE,
    CONSUME
  }
}
