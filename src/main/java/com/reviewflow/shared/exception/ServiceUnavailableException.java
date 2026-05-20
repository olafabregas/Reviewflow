package com.reviewflow.shared.exception;

public class ServiceUnavailableException extends BusinessRuleException {

  public ServiceUnavailableException(String message) {
    super(message, "SERVICE_UNAVAILABLE");
  }
}
