package com.reviewflow.submission.exception;

public class PreviewNotSupportedException extends RuntimeException {

  private final String code;

  public PreviewNotSupportedException(String mimeType) {
    super(String.format("File type '%s' does not support inline preview", mimeType));
    this.code = "PREVIEW_NOT_SUPPORTED";
  }

  public String getCode() {
    return code;
  }
}
