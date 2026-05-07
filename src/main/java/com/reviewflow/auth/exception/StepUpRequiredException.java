package com.reviewflow.auth.exception;

import java.util.Map;
import lombok.Getter;

@Getter
public class StepUpRequiredException extends RuntimeException {

  private final Map<String, Object> details;

  public StepUpRequiredException(String message, Map<String, Object> details) {
    super(message);
    this.details = details;
  }
}
