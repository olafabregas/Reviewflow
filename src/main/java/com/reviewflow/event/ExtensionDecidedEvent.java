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
    // TODO [STYLE-AGENT]: fix structural violation
    List<Long> recipientUserIds) {}
