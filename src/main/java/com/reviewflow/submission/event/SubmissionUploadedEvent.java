package com.reviewflow.submission.event;

import com.reviewflow.shared.domain.SubmissionType;
import java.util.List;

public record SubmissionUploadedEvent(
    List<Long> recipientUserIds, // all ACCEPTED members EXCEPT the uploader
    String uploaderName,
    Long teamId,
    Long studentId,
    String teamName,
    Long assignmentId,
    String assignmentTitle,
    int versionNumber,
    SubmissionType submissionType,
    // TODO [STYLE-AGENT]: fix structural violation
    Long submissionId) {}
