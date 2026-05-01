package com.reviewflow.extension.exception;

import com.reviewflow.shared.exception.ValidationException;

public class InvalidRequestedDateException extends ValidationException {

  public InvalidRequestedDateException(String message) {
    super(message, "INVALID_REQUESTED_DATE");
  }
}
