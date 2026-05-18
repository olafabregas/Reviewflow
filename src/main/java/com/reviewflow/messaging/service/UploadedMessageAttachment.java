package com.reviewflow.messaging.service;

/** Attachment metadata after successful S3 upload. */
record UploadedMessageAttachment(
    String fileName, long fileSizeBytes, String contentType, String storagePath) {}
