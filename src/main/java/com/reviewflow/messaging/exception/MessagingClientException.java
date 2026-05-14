package com.reviewflow.messaging.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MessagingClientException extends RuntimeException {

  private final String code;
  private final HttpStatus httpStatus;

  public MessagingClientException(String code, String message, HttpStatus httpStatus) {
    super(message);
    this.code = code;
    this.httpStatus = httpStatus;
  }

  public static MessagingClientException badRequest(String code, String message) {
    return new MessagingClientException(code, message, HttpStatus.BAD_REQUEST);
  }

  public static MessagingClientException forbidden(String code, String message) {
    return new MessagingClientException(code, message, HttpStatus.FORBIDDEN);
  }

  public static MessagingClientException conflict(String code, String message) {
    return new MessagingClientException(code, message, HttpStatus.CONFLICT);
  }

  public static MessagingClientException tooManyRequests(String code, String message) {
    return new MessagingClientException(code, message, HttpStatus.TOO_MANY_REQUESTS);
  }
}
