package com.reviewflow.extension.event;

import java.time.Instant;
import java.util.List;

public record ExtensionRequestedEvent(
    Long extensionRequestId,
    Long assignmentId,
    String assignmentTitle,
    String studentName,
    Instant requestedDueAt,
    String reason,
    // TODO [STYLE-AGENT]: fix structural violation
    List<Long> instructorUserIds) {}
