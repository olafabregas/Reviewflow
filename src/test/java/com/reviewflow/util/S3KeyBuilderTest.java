package com.reviewflow.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class S3KeyBuilderTest {

    @Test
    void submissionKey_buildsExpectedPath() {
        String key = S3KeyBuilder.submissionKey("A1B2", "T9Z8", 3, "Project Report.zip");
        assertEquals("submissions/A1B2/T9Z8/v3/project_report.zip", key);
    }

    @Test
    void pdfKey_buildsExpectedPath() {
        String key = S3KeyBuilder.pdfKey("EVAL777");
        assertEquals("pdfs/EVAL777/report.pdf", key);
    }

    @Test
    void avatarKey_buildsExpectedPath() {
        String key = S3KeyBuilder.avatarKey("USER123", "JPG");
        assertEquals("avatars/USER123/avatar.jpg", key);
    }

    @Test
    void sanitizeFilename_replacesUnsafeCharactersAndTruncates() {
        String unsafe = "My Report (Final)#Version@2026 with spaces and symbols that should be trimmed because this is definitely longer than one hundred chars.pdf";
        String sanitized = S3KeyBuilder.sanitizeFilename(unsafe);

        assertTrue(sanitized.matches("[a-z0-9._-]+"));
        assertTrue(sanitized.length() <= 100);
    }
}
