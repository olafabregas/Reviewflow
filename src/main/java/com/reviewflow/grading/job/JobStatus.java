package com.reviewflow.grading.job;

public enum JobStatus {
  UPLOADED,
  VALIDATING,
  VALIDATION_FAILED,
  VALIDATION_PASSED,
  COMMITTING,
  COMPLETED,
  FAILED,
  PARTIALLY_COMPLETED
}
