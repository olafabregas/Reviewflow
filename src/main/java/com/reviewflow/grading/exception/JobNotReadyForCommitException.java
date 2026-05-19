package com.reviewflow.grading.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class JobNotReadyForCommitException extends BusinessRuleException {

  public JobNotReadyForCommitException(String message) {
    super(message, "JOB_NOT_READY_FOR_COMMIT");
  }
}
