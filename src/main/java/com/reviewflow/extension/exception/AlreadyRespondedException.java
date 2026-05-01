package com.reviewflow.extension.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class AlreadyRespondedException extends BusinessRuleException {

  public AlreadyRespondedException(String message) {
    super(message, "ALREADY_RESPONDED");
  }
}
