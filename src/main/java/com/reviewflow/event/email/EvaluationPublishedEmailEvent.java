package com.reviewflow.event.email;

import lombok.Getter;

@Getter
public class EvaluationPublishedEmailEvent extends EmailEvent {

    private final String assignmentTitle;
    private final String courseCode;
    private final Integer totalScore;
    private final String evaluationHashId;

    public EvaluationPublishedEmailEvent(
            String recipientEmail,
            String recipientName,
            String assignmentTitle,
            String courseCode,
            Integer totalScore,
            String evaluationHashId) {
        super(recipientEmail, recipientName, EmailCategory.CRITICAL);
        this.assignmentTitle = assignmentTitle;
        this.courseCode = courseCode;
        this.totalScore = totalScore;
        this.evaluationHashId = evaluationHashId;
    }
}
