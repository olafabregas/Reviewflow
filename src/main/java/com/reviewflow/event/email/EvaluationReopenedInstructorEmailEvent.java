package com.reviewflow.event.email;

import org.springframework.context.ApplicationEvent;

public class EvaluationReopenedInstructorEmailEvent extends ApplicationEvent {

    private final String instructorEmail;
    private final String reason;
    private final String scoreSnapshot;

    public EvaluationReopenedInstructorEmailEvent(Object source, String instructorEmail, String reason, String scoreSnapshot) {
        super(source);
        this.instructorEmail = instructorEmail;
        this.reason = reason;
        this.scoreSnapshot = scoreSnapshot;
    }

    public String getInstructorEmail() {
        return instructorEmail;
    }

    public String getReason() {
        return reason;
    }

    public String getScoreSnapshot() {
        return scoreSnapshot;
    }
}
