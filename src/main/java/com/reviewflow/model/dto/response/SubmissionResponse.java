package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class SubmissionResponse {
    String id;
    String teamId;
    String teamName;
    String assignmentId;
    String assignmentTitle;
    String courseCode;
    Integer versionNumber;
    String fileName;
    Long fileSizeBytes;
    Boolean isLate;
    Instant uploadedAt;
    String changeNote;
    String uploadedById;
    String uploadedByName;
}
