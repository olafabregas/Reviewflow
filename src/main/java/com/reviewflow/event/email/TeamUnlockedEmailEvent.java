package com.reviewflow.event.email;

import org.springframework.context.ApplicationEvent;

public class TeamUnlockedEmailEvent extends ApplicationEvent {

    private final String userEmail;
    private final String fullName;
    private final String teamName;
    private final String reason;

    public TeamUnlockedEmailEvent(Object source, String userEmail, String fullName, String teamName, String reason) {
        super(source);
        this.userEmail = userEmail;
        this.fullName = fullName;
        this.teamName = teamName;
        this.reason = reason;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getFullName() {
        return fullName;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getReason() {
        return reason;
    }
}
