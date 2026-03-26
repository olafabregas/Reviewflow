package com.reviewflow.exception;

public class InvalidRequestedDateException extends ValidationException {

    public InvalidRequestedDateException(String message) {
        super(message, "INVALID_REQUESTED_DATE");
    }
}
