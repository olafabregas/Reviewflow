package com.reviewflow.event;

import java.util.List;

public record EvaluationPublishedEvent(
        List<Long> recipientUserIds,    // all ACCEPTED team members
        Long       evaluationId,
        Long       assignmentId,
        String     assignmentTitle,
        int        totalScore,
        int        maxPossibleScore
) {}
