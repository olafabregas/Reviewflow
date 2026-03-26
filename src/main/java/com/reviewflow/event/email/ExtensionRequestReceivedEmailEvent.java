package com.reviewflow.event.email;

import java.time.Instant;

import lombok.Getter;

@Getter
public class ExtensionRequestReceivedEmailEvent extends EmailEvent {

    private final String studentName;
    private final String assignmentTitle;
    private final Instant requestedDueAt;
    private final String reason;
    private final String extensionRequestHashId;

    public ExtensionRequestReceivedEmailEvent(
            String recipientEmail,
            String recipientName,
            String studentName,
            String assignmentTitle,
            Instant requestedDueAt,
            String reason,
            String extensionRequestHashId) {
        super(recipientEmail, recipientName, EmailCategory.STANDARD);
        this.studentName = studentName;
        this.assignmentTitle = assignmentTitle;
        this.requestedDueAt = requestedDueAt;
        this.reason = reason;
        this.extensionRequestHashId = extensionRequestHashId;
    }
}
