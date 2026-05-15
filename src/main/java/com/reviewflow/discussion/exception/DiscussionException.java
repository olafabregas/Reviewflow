package com.reviewflow.discussion.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DiscussionException extends RuntimeException {

  private final HttpStatus status;
  private final String errorCode;
  private final Object data;

  public DiscussionException(HttpStatus status, String errorCode, String message) {
    this(status, errorCode, message, null);
  }

  public DiscussionException(HttpStatus status, String errorCode, String message, Object data) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
    this.data = data;
  }
}
