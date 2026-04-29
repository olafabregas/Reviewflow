package com.reviewflow.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class TeamSubmissionRequiredException extends BusinessRuleException {

  public TeamSubmissionRequiredException(String message) {
    super(message, "TEAM_SUBMISSION_REQUIRED");
  }
}
