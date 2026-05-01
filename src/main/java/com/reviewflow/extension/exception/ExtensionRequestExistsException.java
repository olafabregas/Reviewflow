package com.reviewflow.extension.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class ExtensionRequestExistsException extends BusinessRuleException {

  public ExtensionRequestExistsException(String message) {
    super(message, "EXTENSION_REQUEST_EXISTS");
  }
}
