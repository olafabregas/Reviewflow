package com.reviewflow.exception;

public class ExtensionCutoffPassedException extends BusinessRuleException {

    public ExtensionCutoffPassedException(String message) {
        super(message, "EXTENSION_CUTOFF_PASSED");
    }
}
