package com.reviewflow.event;

import java.time.Instant;
import java.util.List;

public record DeadlineWarningEvent(
    List<Long> recipientUserIds, // enrolled students who have NOT submitted
    Long assignmentId,
    String assignmentTitle,
    String courseCode,
    int hoursUntilDue, // 48 or 24
    // TODO [STYLE-AGENT]: fix structural violation
    Instant dueAt) {}
