package com.reviewflow.infrastructure.ratelimit;

import com.reviewflow.shared.domain.UserRole;

public interface RateLimitService {

  RateLimitResult probe(String key, RateLimitStrategy strategy, UserRole role);

  RateLimitResult tryConsume(String key, RateLimitStrategy strategy, UserRole role);

  RateLimitResult consumeOnFailure(String key, RateLimitStrategy strategy, UserRole role);

  void reset(String key, RateLimitStrategy strategy);
}
