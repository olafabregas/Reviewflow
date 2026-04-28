package com.reviewflow.exception;

/**
 * Thrown when a session has exceeded its lifetime limits. Covers both: absolute session ceiling
 * (SESSION_CEILING_REACHED) and idle session timeout (SESSION_IDLE_EXPIRED).
 */
public class SessionExpiredException extends RuntimeException {

  private final String errorCode;

  public SessionExpiredException(String message, String errorCode) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
