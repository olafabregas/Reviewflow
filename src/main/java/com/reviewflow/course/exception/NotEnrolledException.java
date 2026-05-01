package com.reviewflow.course.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class NotEnrolledException extends BusinessRuleException {

  public NotEnrolledException(String message) {
    super(message, "NOT_ENROLLED");
  }
}
