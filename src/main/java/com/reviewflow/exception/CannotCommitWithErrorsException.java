package com.reviewflow.exception;

import com.reviewflow.shared.exception.ValidationException;

public class CannotCommitWithErrorsException extends ValidationException {

  public CannotCommitWithErrorsException(String message) {
    super(message, "CANNOT_COMMIT_WITH_ERRORS");
  }
}
