package com.reviewflow.assignment.exception;

import com.reviewflow.shared.exception.ValidationException;

public class InvalidGroupWeightException extends ValidationException {

  public InvalidGroupWeightException(String message) {
    super(message, "INVALID_GROUP_WEIGHT");
  }
}
