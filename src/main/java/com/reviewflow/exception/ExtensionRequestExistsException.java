package com.reviewflow.exception;

public class ExtensionRequestExistsException extends BusinessRuleException {

    public ExtensionRequestExistsException(String message) {
        super(message, "EXTENSION_REQUEST_EXISTS");
    }
}
