package com.reviewflow.grading.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class NoErrorsToDownloadException extends BusinessRuleException {

  public NoErrorsToDownloadException(String message) {
    super(message, "NO_ERRORS_TO_DOWNLOAD");
  }
}
