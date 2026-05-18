package com.reviewflow.infrastructure.ratelimit;

public record RateLimitResult(
    boolean allowed,
    long remainingTokens,
    long retryAfterSeconds,
    long limitCapacity,
    long resetEpochSeconds,
    RateLimitStrategy strategy) {

  public static RateLimitResult allowed(RateLimitStrategy strategy, long remaining, long limit) {
    return new RateLimitResult(true, remaining, 0, limit, 0, strategy);
  }

  public static RateLimitResult allowedFailOpen(RateLimitStrategy strategy) {
    return new RateLimitResult(true, Long.MAX_VALUE, 0, 0, 0, strategy);
  }

  public static RateLimitResult denied(
      RateLimitStrategy strategy,
      long retryAfterSeconds,
      long limitCapacity,
      long resetEpochSeconds) {
    return new RateLimitResult(
        false, 0, retryAfterSeconds, limitCapacity, resetEpochSeconds, strategy);
  }
}
