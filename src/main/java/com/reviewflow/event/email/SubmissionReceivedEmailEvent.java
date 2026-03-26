package com.reviewflow.event.email;

import lombok.Getter;

@Getter
public class SubmissionReceivedEmailEvent extends EmailEvent {

    private final String teamOrStudentName;
    private final String assignmentTitle;
    private final String submissionHashId;
    private final Integer versionNumber;

    public SubmissionReceivedEmailEvent(
            String recipientEmail,
            String recipientName,
            String teamOrStudentName,
            String assignmentTitle,
            String submissionHashId,
            Integer versionNumber) {
        super(recipientEmail, recipientName, EmailCategory.STANDARD);
        this.teamOrStudentName = teamOrStudentName;
        this.assignmentTitle = assignmentTitle;
        this.submissionHashId = submissionHashId;
        this.versionNumber = versionNumber;
    }
}
