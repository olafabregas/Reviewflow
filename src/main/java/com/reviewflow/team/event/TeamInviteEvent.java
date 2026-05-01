package com.reviewflow.team.event;

public record TeamInviteEvent(
    Long inviteeUserId,
    Long teamId,
    String teamName,
    String invitedByFirstName,
    Long assignmentId,
    // TODO [STYLE-AGENT]: fix structural violation
    String assignmentTitle) {}
