package com.reviewflow.service;

import java.util.Set;

public record ValidationConfig(
        Set<String> allowedExtensions,
        Set<String> allowedMimeTypes,
        long maxFileSizeBytes
        ) {

}
