package com.reviewflow.event;

import com.reviewflow.model.enums.SubmissionType;
import java.util.List;

public record EvaluationPublishedEvent(
        List<Long> recipientUserIds,    // all ACCEPTED team members
        Long       studentId,
        Long       evaluationId,
        Long       assignmentId,
        String     assignmentTitle,
        int        totalScore,
        int        maxPossibleScore,
        SubmissionType submissionType
) {}
