package com.reviewflow.grading.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class ImportInProgressException extends BusinessRuleException {

  public ImportInProgressException(String message) {
    super(message, "IMPORT_IN_PROGRESS");
  }
}
