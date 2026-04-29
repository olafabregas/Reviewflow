package com.reviewflow.exception;

import com.reviewflow.shared.exception.ValidationException;

public class ImportSessionExpiredException extends ValidationException {

  public ImportSessionExpiredException(String message) {
    super(message, "IMPORT_SESSION_EXPIRED");
  }
}
