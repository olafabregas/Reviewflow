package com.reviewflow.assignment.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class GroupNotEmptyException extends BusinessRuleException {

  public GroupNotEmptyException(String message) {
    super(message, "GROUP_NOT_EMPTY");
  }
}
