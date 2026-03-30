package com.reviewflow.event.email;

import org.springframework.context.ApplicationEvent;

public class ForceLogoutEmailEvent extends ApplicationEvent {

    private final String userEmail;
    private final String reason;
    private final int revokedTokenCount;

    public ForceLogoutEmailEvent(Object source, String userEmail, String reason, int revokedTokenCount) {
        super(source);
        this.userEmail = userEmail;
        this.reason = reason;
        this.revokedTokenCount = revokedTokenCount;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getReason() {
        return reason;
    }

    public int getRevokedTokenCount() {
        return revokedTokenCount;
    }
}
