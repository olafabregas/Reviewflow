package com.reviewflow.event.email;

import lombok.Getter;

@Getter
public class TeamInviteRespondedEmailEvent extends EmailEvent {

    private final String inviteeName;
    private final Boolean accepted;
    private final String teamName;
    private final String assignmentTitle;

    public TeamInviteRespondedEmailEvent(
            String recipientEmail,
            String recipientName,
            String inviteeName,
            Boolean accepted,
            String teamName,
            String assignmentTitle) {
        super(recipientEmail, recipientName, EmailCategory.STANDARD);
        this.inviteeName = inviteeName;
        this.accepted = accepted;
        this.teamName = teamName;
        this.assignmentTitle = assignmentTitle;
    }
}
