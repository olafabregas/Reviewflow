package com.reviewflow.event.email;

import org.springframework.context.ApplicationEvent;

public class EvaluationReopenedTeamEmailEvent extends ApplicationEvent {

    private final String teamMemberEmail;
    private final String reason;

    public EvaluationReopenedTeamEmailEvent(Object source, String teamMemberEmail, String reason) {
        super(source);
        this.teamMemberEmail = teamMemberEmail;
        this.reason = reason;
    }

    public String getTeamMemberEmail() {
        return teamMemberEmail;
    }

    public String getReason() {
        return reason;
    }
}
