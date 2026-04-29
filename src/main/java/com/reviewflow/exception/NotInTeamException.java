package com.reviewflow.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class NotInTeamException extends BusinessRuleException {

  public NotInTeamException(String message) {
    super(message, "NOT_IN_TEAM");
  }
}
