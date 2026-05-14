package com.reviewflow.infrastructure.config;

import java.util.Set;

public record ValidationConfig(
    // TODO [STYLE-AGENT]: fix structural violation
    Set<String> allowedExtensions, Set<String> allowedMimeTypes, long maxFileSizeBytes) {

  /** PRD-18 message attachments (same allowlist as PRD). */
  public static final ValidationConfig MESSAGE =
      new ValidationConfig(
          Set.of(
              "pdf",
              "docx",
              "doc",
              "pptx",
              "ppt",
              "jpg",
              "jpeg",
              "png",
              "webp",
              "gif",
              "zip",
              "txt",
              "csv"),
          Set.of(
              "application/pdf",
              "application/msword",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
              "application/vnd.ms-powerpoint",
              "application/vnd.openxmlformats-officedocument.presentationml.presentation",
              "image/jpeg",
              "image/png",
              "image/webp",
              "image/gif",
              "application/zip",
              "text/plain",
              "text/csv"),
          25L * 1024 * 1024);
}
