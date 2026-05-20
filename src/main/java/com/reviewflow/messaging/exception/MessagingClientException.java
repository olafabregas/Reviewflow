package com.reviewflow.messaging.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MessagingClientException extends RuntimeException {

  private final String code;
  private final HttpStatus httpStatus;
  private final long retryAfterSeconds;

  public MessagingClientException(
      String code, String message, HttpStatus httpStatus, long retryAfterSeconds) {
    super(message);
    this.code = code;
    this.httpStatus = httpStatus;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public static MessagingClientException badRequest(String code, String message) {
    return new MessagingClientException(code, message, HttpStatus.BAD_REQUEST, 0);
  }

  public static MessagingClientException forbidden(String code, String message) {
    return new MessagingClientException(code, message, HttpStatus.FORBIDDEN, 0);
  }

  public static MessagingClientException conflict(String code, String message) {
    return new MessagingClientException(code, message, HttpStatus.CONFLICT, 0);
  }

  public static MessagingClientException tooManyRequests(String code, String message) {
    return tooManyRequests(code, message, 0);
  }

  public static MessagingClientException tooManyRequests(
      String code, String message, long retryAfterSeconds) {
    return new MessagingClientException(
        code, message, HttpStatus.TOO_MANY_REQUESTS, retryAfterSeconds);
  }
}
