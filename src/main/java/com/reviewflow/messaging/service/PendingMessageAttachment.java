package com.reviewflow.messaging.service;

/** Attachment payload staged before S3 upload completes. */
record PendingMessageAttachment(
    String fileName, long fileSizeBytes, String contentType, byte[] bytes) {}
