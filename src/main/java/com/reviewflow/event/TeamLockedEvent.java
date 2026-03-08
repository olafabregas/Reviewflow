package com.reviewflow.event;

import java.util.List;

public record TeamLockedEvent(
        List<Long> recipientUserIds,    // all ACCEPTED team members
        Long       teamId,
        String     teamName,
        Long       assignmentId,
        String     assignmentTitle
) {}
