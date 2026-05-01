package com.reviewflow.submission.exception;

public class ArchiveTooLargeException extends RuntimeException {
  private final String code;

  public ArchiveTooLargeException(String message) {
    super(message);
    this.code = "ARCHIVE_TOO_LARGE";
  }

  public String getCode() {
    return code;
  }
}
