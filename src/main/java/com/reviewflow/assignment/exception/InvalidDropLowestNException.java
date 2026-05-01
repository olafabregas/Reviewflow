package com.reviewflow.assignment.exception;

import com.reviewflow.shared.exception.ValidationException;

public class InvalidDropLowestNException extends ValidationException {

  public InvalidDropLowestNException(String message) {
    super(message, "DROP_LOWEST_EXCEEDS_GROUP_SIZE");
  }
}
