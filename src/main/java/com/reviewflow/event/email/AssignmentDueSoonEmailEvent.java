package com.reviewflow.event.email;

import java.time.Instant;

import lombok.Getter;

@Getter
public class AssignmentDueSoonEmailEvent extends EmailEvent {

    private final String assignmentTitle;
    private final Instant dueAt;
    private final String courseCode;
    private final String assignmentHashId;

    public AssignmentDueSoonEmailEvent(
            String recipientEmail,
            String recipientName,
            String assignmentTitle,
            Instant dueAt,
            String courseCode,
            String assignmentHashId) {
        super(recipientEmail, recipientName, EmailCategory.STANDARD);
        this.assignmentTitle = assignmentTitle;
        this.dueAt = dueAt;
        this.courseCode = courseCode;
        this.assignmentHashId = assignmentHashId;
    }
}
