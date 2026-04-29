package com.reviewflow.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class SubmissionTypeLockedException extends BusinessRuleException {

  public SubmissionTypeLockedException(String message) {
    super(message, "SUBMISSION_TYPE_LOCKED");
  }
}
