package com.reviewflow.model.dto.response;

import java.time.Instant;

import com.reviewflow.model.enums.SubmissionType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SubmissionResponse {
    String id;
    SubmissionType submissionType;
    String studentId;
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
