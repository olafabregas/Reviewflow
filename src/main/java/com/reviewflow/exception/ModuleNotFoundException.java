package com.reviewflow.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class ModuleNotFoundException extends BusinessRuleException {

  public ModuleNotFoundException(String message) {
    super(message, "MODULE_NOT_FOUND");
  }
}
