package com.reviewflow.exception;

public class SubmissionTypeLockedException extends BusinessRuleException {

    public SubmissionTypeLockedException(String message) {
        super(message, "SUBMISSION_TYPE_LOCKED");
    }
}
