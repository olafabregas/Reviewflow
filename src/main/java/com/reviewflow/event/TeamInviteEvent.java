package com.reviewflow.event;

public record TeamInviteEvent(
        Long   inviteeUserId,
        Long   teamId,
        String teamName,
        String invitedByFirstName,
        Long   assignmentId,
        String assignmentTitle
) {}
