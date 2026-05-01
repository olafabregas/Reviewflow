package com.reviewflow.grading.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class SubmissionNotRequiredException extends BusinessRuleException {

  public SubmissionNotRequiredException(String message) {
    super(message, "SUBMISSION_NOT_REQUIRED");
  }
}
