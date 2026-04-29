package com.reviewflow.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class TeamNotAllowedException extends BusinessRuleException {

  public TeamNotAllowedException(String message) {
    super(message, "TEAM_NOT_ALLOWED");
  }
}
