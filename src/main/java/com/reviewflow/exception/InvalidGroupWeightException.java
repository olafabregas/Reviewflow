package com.reviewflow.exception;

public class InvalidGroupWeightException extends ValidationException {

    public InvalidGroupWeightException(String message) {
        super(message, "INVALID_GROUP_WEIGHT");
    }
}
