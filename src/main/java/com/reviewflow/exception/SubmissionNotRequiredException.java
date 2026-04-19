package com.reviewflow.exception;

public class SubmissionNotRequiredException extends BusinessRuleException {

    public SubmissionNotRequiredException(String message) {
        super(message, "SUBMISSION_NOT_REQUIRED");
    }
}
