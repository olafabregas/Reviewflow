package com.reviewflow.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class IndividualSubmissionOnlyException extends BusinessRuleException {

  public IndividualSubmissionOnlyException(String message) {
    super(message, "INDIVIDUAL_SUBMISSION_ONLY");
  }
}
