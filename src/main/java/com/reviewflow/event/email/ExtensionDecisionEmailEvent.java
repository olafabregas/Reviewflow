package com.reviewflow.event.email;

import java.time.Instant;

import lombok.Getter;

@Getter
public class ExtensionDecisionEmailEvent extends EmailEvent {

    private final String assignmentTitle;
    private final Boolean approved;
    private final String instructorNote;
    private final Instant newDueAt;

    public ExtensionDecisionEmailEvent(
            String recipientEmail,
            String recipientName,
            String assignmentTitle,
            Boolean approved,
            String instructorNote,
            Instant newDueAt) {
        super(recipientEmail, recipientName, EmailCategory.CRITICAL);
        this.assignmentTitle = assignmentTitle;
        this.approved = approved;
        this.instructorNote = instructorNote;
        this.newDueAt = newDueAt;
    }
}
