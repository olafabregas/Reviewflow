package com.reviewflow.event;

import java.time.Instant;
import java.util.List;

public record ExtensionDecidedEvent(
        Long extensionRequestId,
        Long assignmentId,
        String assignmentTitle,
        Boolean approved,
        String instructorNote,
        Instant newDueAt,
        List<Long> recipientUserIds
        ) {

}
