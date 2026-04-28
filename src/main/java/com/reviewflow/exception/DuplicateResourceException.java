package com.reviewflow.exception;

public class DuplicateResourceException extends RuntimeException {

  private final String code;

  public DuplicateResourceException(String message, String code) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
