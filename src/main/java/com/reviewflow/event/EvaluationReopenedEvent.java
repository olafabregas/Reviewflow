package com.reviewflow.event;

import org.springframework.context.ApplicationEvent;

import java.util.List;

public class EvaluationReopenedEvent extends ApplicationEvent {

    private final Long evaluationId;
    private final Long teamId;
    private final String instructorEmail;
    private final List<String> teamMemberEmails;
    private final String scoringSnapshot;
    private final String reason;

    public EvaluationReopenedEvent(Object source, Long evaluationId, Long teamId, String instructorEmail,
            List<String> teamMemberEmails, String scoringSnapshot, String reason) {
        super(source);
        this.evaluationId = evaluationId;
        this.teamId = teamId;
        this.instructorEmail = instructorEmail;
        this.teamMemberEmails = teamMemberEmails;
        this.scoringSnapshot = scoringSnapshot;
        this.reason = reason;
    }

    public Long getEvaluationId() {
        return evaluationId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public String getInstructorEmail() {
        return instructorEmail;
    }

    public List<String> getTeamMemberEmails() {
        return teamMemberEmails;
    }

    public String getScoringSnapshot() {
        return scoringSnapshot;
    }

    public String getReason() {
        return reason;
    }
}
