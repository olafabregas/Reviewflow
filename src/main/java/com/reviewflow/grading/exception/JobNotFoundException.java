package com.reviewflow.grading.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class JobNotFoundException extends BusinessRuleException {

  public JobNotFoundException(String jobId) {
    super("Import job not found or expired: " + jobId, "JOB_NOT_FOUND");
  }
}
