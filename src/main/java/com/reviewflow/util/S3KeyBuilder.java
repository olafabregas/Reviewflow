package com.reviewflow.util;

import java.util.Locale;

public final class S3KeyBuilder {

    private static final int MAX_FILENAME_LENGTH = 100;

    private S3KeyBuilder() {
    }

    public static String submissionKey(String hashedAssignmentId,
                                       String hashedOwnerIdTeamOrStudent,
                                       int version,
                                       String originalFilename) {
        String sanitized = sanitizeFilename(originalFilename);
        return String.format("submissions/%s/%s/v%d/%s",
                hashedAssignmentId,
                hashedOwnerIdTeamOrStudent,
                version,
                sanitized);
    }

    public static String pdfKey(String hashedEvaluationId) {
        return String.format("pdfs/%s/report.pdf", hashedEvaluationId);
    }

    public static String avatarKey(String hashedUserId, String ext) {
        return String.format("avatars/%s/avatar.%s", hashedUserId, ext.toLowerCase(Locale.ROOT));
    }

    public static String sanitizeFilename(String filename) {
        String safe = filename == null || filename.isBlank() ? "upload" : filename;
        safe = safe.replaceAll("[^a-zA-Z0-9.\\-_]", "_")
                .toLowerCase(Locale.ROOT);
        if (safe.length() <= MAX_FILENAME_LENGTH) {
            return safe;
        }
        return safe.substring(0, MAX_FILENAME_LENGTH);
    }
}
