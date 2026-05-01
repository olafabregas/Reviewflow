package com.reviewflow.shared.exception;

public class InvalidRoleException extends RuntimeException {

  private final String code;

  public InvalidRoleException(String message, String code) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
