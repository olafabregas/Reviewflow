package com.reviewflow.event;

import java.util.List;

public record SubmissionUploadedEvent(
        List<Long> recipientUserIds,    // all ACCEPTED members EXCEPT the uploader
        String     uploaderName,
        Long       teamId,
        String     teamName,
        Long       assignmentId,
        String     assignmentTitle,
        int        versionNumber
) {}
