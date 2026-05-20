package com.reviewflow.shared.exception;

import lombok.Getter;

@Getter
public class RateLimitException extends RuntimeException {
  private final long retryAfterSeconds;

  public RateLimitException(String message, long retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public RateLimitException(String message) {
    this(message, 60);
  }
}
