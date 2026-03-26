package com.reviewflow.event;

import com.reviewflow.model.enums.SubmissionType;
import java.util.List;

public record SubmissionUploadedEvent(
        List<Long> recipientUserIds,    // all ACCEPTED members EXCEPT the uploader
        String     uploaderName,
        Long       teamId,
        Long       studentId,
        String     teamName,
        Long       assignmentId,
        String     assignmentTitle,
        int        versionNumber,
        SubmissionType submissionType,
        Long       submissionId
) {}
