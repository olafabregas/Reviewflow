package com.reviewflow.exception;

public class ImportSessionExpiredException extends ValidationException {

    public ImportSessionExpiredException(String message) {
        super(message, "IMPORT_SESSION_EXPIRED");
    }
}
