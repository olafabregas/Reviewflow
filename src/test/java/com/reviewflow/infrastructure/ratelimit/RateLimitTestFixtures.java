package com.reviewflow.infrastructure.ratelimit;

import java.time.Instant;

public final class RateLimitTestFixtures {

  private RateLimitTestFixtures() {}

  public static RateLimitResult allowed(RateLimitStrategy strategy) {
    return RateLimitResult.allowed(strategy, 100, 100);
  }

  public static RateLimitResult denied(RateLimitStrategy strategy) {
    return RateLimitResult.denied(strategy, 60, 10, Instant.now().getEpochSecond() + 60);
  }
}
