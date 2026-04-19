package com.reviewflow.exception;

public class NotEnrolledException extends BusinessRuleException {

    public NotEnrolledException(String message) {
        super(message, "NOT_ENROLLED");
    }
}
