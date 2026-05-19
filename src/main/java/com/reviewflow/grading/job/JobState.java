package com.reviewflow.grading.job;

import java.time.Instant;

public record JobState(
    String jobId,
    JobStatus status,
    String assignmentId,
    String instructorId,
    String courseId,
    int totalRows,
    int validRows,
    int invalidRows,
    int processedRows,
    String sourceS3Key,
    String validatedRowsS3Key,
    String errorCsvS3Key,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt) {}
