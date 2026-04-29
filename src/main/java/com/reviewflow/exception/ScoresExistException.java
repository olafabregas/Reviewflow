package com.reviewflow.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class ScoresExistException extends BusinessRuleException {

  public ScoresExistException(String message) {
    super(message, "SCORES_EXIST");
  }
}
