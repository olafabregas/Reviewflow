package com.reviewflow.grading.exception;

import com.reviewflow.shared.exception.ValidationException;

public class ScoreExceedsMaxException extends ValidationException {

  public ScoreExceedsMaxException(String message) {
    super(message, "SCORE_EXCEEDS_MAX");
  }
}
