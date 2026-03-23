package com.reviewflow.exception;

public class IndividualSubmissionOnlyException extends BusinessRuleException {

    public IndividualSubmissionOnlyException(String message) {
        super(message, "INDIVIDUAL_SUBMISSION_ONLY");
    }
}
