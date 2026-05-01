package com.reviewflow.grading.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class ScoreNotPublishedException extends BusinessRuleException {

  public ScoreNotPublishedException(String message) {
    super(message, "SCORE_NOT_PUBLISHED");
  }
}
