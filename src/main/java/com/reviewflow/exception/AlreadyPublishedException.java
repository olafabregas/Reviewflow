package com.reviewflow.exception;

public class AlreadyPublishedException extends BusinessRuleException {

    public AlreadyPublishedException(String message) {
        super(message, "ALREADY_PUBLISHED");
    }

    public AlreadyPublishedException() {
        super("This announcement is already published", "ALREADY_PUBLISHED");
    }
}
