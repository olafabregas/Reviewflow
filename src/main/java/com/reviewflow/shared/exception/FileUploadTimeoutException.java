package com.reviewflow.shared.exception;

public class FileUploadTimeoutException extends BusinessRuleException {

  public FileUploadTimeoutException(int timeoutSeconds) {
    super("Upload timed out after " + timeoutSeconds + " seconds", "FILE_UPLOAD_TIMEOUT");
  }
}
