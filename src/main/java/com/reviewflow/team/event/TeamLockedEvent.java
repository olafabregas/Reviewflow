package com.reviewflow.team.event;

import java.util.List;

public record TeamLockedEvent(
    List<Long> recipientUserIds, // all ACCEPTED team members
    Long teamId,
    String teamName,
    Long assignmentId,
    // TODO [STYLE-AGENT]: fix structural violation
    String assignmentTitle) {}
