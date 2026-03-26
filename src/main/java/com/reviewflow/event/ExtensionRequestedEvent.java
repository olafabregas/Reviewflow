package com.reviewflow.event;

import java.time.Instant;
import java.util.List;

public record ExtensionRequestedEvent(
        Long extensionRequestId,
        Long assignmentId,
        String assignmentTitle,
        String studentName,
        Instant requestedDueAt,
        String reason,
        List<Long> instructorUserIds
        ) {

}
