package com.reviewflow.exception;

public class CannotDeleteUncategorizedException extends BusinessRuleException {

    public CannotDeleteUncategorizedException(String message) {
        super(message, "CANNOT_DELETE_UNCATEGORIZED");
    }
}