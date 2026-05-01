package com.reviewflow.submission.exception;

public class InvalidFileStructureException extends RuntimeException {
  private final String code;

  public InvalidFileStructureException(String message) {
    super(message);
    this.code = "INVALID_FILE_STRUCTURE";
  }

  public String getCode() {
    return code;
  }
}
