package com.reviewflow.notification.event;

import java.time.Instant;
import java.util.List;

public record DeadlineWarningEvent(
    List<Long> recipientUserIds,
    Long assignmentId,
    String assignmentTitle,
    String courseCode,
    int hoursUntilDue,
    Instant dueAt) {}
