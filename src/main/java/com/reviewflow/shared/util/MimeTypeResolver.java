package com.reviewflow.shared.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MimeTypeResolver {

  private static final Map<String, String> EXTENSION_TO_MIME = new HashMap<>();
  private static final Set<String> PREVIEWABLE_MIME_TYPES = new HashSet<>();

  static {
    // Map file extensions to MIME types
    EXTENSION_TO_MIME.put("pdf", "application/pdf");
    EXTENSION_TO_MIME.put("jpg", "image/jpeg");
    EXTENSION_TO_MIME.put("jpeg", "image/jpeg");
    EXTENSION_TO_MIME.put("png", "image/png");
    EXTENSION_TO_MIME.put("webp", "image/webp");

    // Define previewable MIME types
    PREVIEWABLE_MIME_TYPES.add("application/pdf");
    PREVIEWABLE_MIME_TYPES.add("image/jpeg");
    PREVIEWABLE_MIME_TYPES.add("image/png");
    PREVIEWABLE_MIME_TYPES.add("image/webp");
  }

    // TODO [STYLE-AGENT]: fix structural violation
  private MimeTypeResolver() {}

  /**
   * Resolve MIME type from file extension. Returns the MIME type if found, otherwise returns null.
   *
   * @param filename The filename with extension
   * @return The MIME type or null if unknown
   */
  public static String getMimeType(String filename) {
    if (filename == null || filename.isBlank()) {
      return null;
    }

    String extension = getFileExtension(filename);
    return EXTENSION_TO_MIME.getOrDefault(extension.toLowerCase(Locale.ROOT), null);
  }

  /**
   * Check if a MIME type is previewable inline.
   *
   * @param mimeType The MIME type to check
   * @return true if the MIME type supports inline preview
   */
  public static boolean isPreviewable(String mimeType) {
    if (mimeType == null || mimeType.isBlank()) {
      return false;
    }
    return PREVIEWABLE_MIME_TYPES.contains(mimeType.toLowerCase(Locale.ROOT));
  }

  /**
   * Get the file extension from a filename.
   *
   * @param filename The filename
   * @return The file extension without the dot, or empty string if no extension
   */
  private static String getFileExtension(String filename) {
    int lastDot = filename.lastIndexOf('.');
    if (lastDot <= 0 || lastDot == filename.length() - 1) {
      return "";
    }
    return filename.substring(lastDot + 1);
  }
}
