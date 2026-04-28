package com.reviewflow.exception;

public class FileTooLargeForPreviewException extends RuntimeException {

  private final String code;
  private final long maxSizeBytes;

  public FileTooLargeForPreviewException(long fileSizeBytes, long maxSizeBytes) {
    super(
        String.format(
            "File size %,d bytes exceeds preview limit of %,d bytes", fileSizeBytes, maxSizeBytes));
    this.code = "FILE_TOO_LARGE_FOR_PREVIEW";
    this.maxSizeBytes = maxSizeBytes;
  }

  public String getCode() {
    return code;
  }

  public long getMaxSizeBytes() {
    return maxSizeBytes;
  }
}
