package com.reviewflow.exception;

public class TeamSubmissionRequiredException extends BusinessRuleException {

    public TeamSubmissionRequiredException(String message) {
        super(message, "TEAM_SUBMISSION_REQUIRED");
    }
}
