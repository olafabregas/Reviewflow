package com.reviewflow.extension.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class ExtensionCutoffPassedException extends BusinessRuleException {

  public ExtensionCutoffPassedException(String message) {
    super(message, "EXTENSION_CUTOFF_PASSED");
  }
}
