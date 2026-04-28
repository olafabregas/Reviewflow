package com.reviewflow.exception;

/**
 * Thrown when an access token's {@code ver} claim does not match the current token version for the
 * user. This indicates the token was issued before a deactivation or force-logout event.
 */
public class TokenVersionMismatchException extends RuntimeException {

  private final Long userId;

  public TokenVersionMismatchException(Long userId) {
    super("Token version mismatch for user " + userId);
    this.userId = userId;
  }

  public Long getUserId() {
    return userId;
  }
}
