package com.reviewflow.shared.exception;

public class BusinessRuleException extends RuntimeException {

  private final String code;

  public BusinessRuleException(String message, String code) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}