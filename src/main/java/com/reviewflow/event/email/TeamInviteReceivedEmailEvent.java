package com.reviewflow.event.email;

import lombok.Getter;

@Getter
public class TeamInviteReceivedEmailEvent extends EmailEvent {

    private final String inviterName;
    private final String assignmentTitle;
    private final String teamName;
    private final String teamMemberHashId;

    public TeamInviteReceivedEmailEvent(
            String recipientEmail,
            String recipientName,
            String inviterName,
            String assignmentTitle,
            String teamName,
            String teamMemberHashId) {
        super(recipientEmail, recipientName, EmailCategory.STANDARD);
        this.inviterName = inviterName;
        this.assignmentTitle = assignmentTitle;
        this.teamName = teamName;
        this.teamMemberHashId = teamMemberHashId;
    }
}
