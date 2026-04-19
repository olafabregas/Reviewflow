package com.reviewflow.exception;

public class CannotCommitWithErrorsException extends ValidationException {

    public CannotCommitWithErrorsException(String message) {
        super(message, "CANNOT_COMMIT_WITH_ERRORS");
    }
}
