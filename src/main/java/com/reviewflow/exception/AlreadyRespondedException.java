package com.reviewflow.exception;

public class AlreadyRespondedException extends BusinessRuleException {

    public AlreadyRespondedException(String message) {
        super(message, "ALREADY_RESPONDED");
    }
}
