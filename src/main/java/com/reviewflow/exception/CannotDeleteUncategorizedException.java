package com.reviewflow.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class CannotDeleteUncategorizedException extends BusinessRuleException {

  public CannotDeleteUncategorizedException(String message) {
    super(message, "CANNOT_DELETE_UNCATEGORIZED");
  }
}
