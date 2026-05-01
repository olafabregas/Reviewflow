package com.reviewflow.infrastructure.config;

import java.util.Set;

public record ValidationConfig(
    // TODO [STYLE-AGENT]: fix structural violation
    Set<String> allowedExtensions, Set<String> allowedMimeTypes, long maxFileSizeBytes) {}
