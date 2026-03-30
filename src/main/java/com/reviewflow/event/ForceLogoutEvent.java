package com.reviewflow.event;

import org.springframework.context.ApplicationEvent;

public class ForceLogoutEvent extends ApplicationEvent {

    private final Long targetUserId;
    private final String targetUserEmail;
    private final String reason;
    private final int revokedTokenCount;

    public ForceLogoutEvent(Object source, Long targetUserId, String targetUserEmail, String reason, int revokedTokenCount) {
        super(source);
        this.targetUserId = targetUserId;
        this.targetUserEmail = targetUserEmail;
        this.reason = reason;
        this.revokedTokenCount = revokedTokenCount;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public String getTargetUserEmail() {
        return targetUserEmail;
    }

    public String getReason() {
        return reason;
    }

    public int getRevokedTokenCount() {
        return revokedTokenCount;
    }
}
