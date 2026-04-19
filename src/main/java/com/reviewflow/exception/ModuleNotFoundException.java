package com.reviewflow.exception;

public class ModuleNotFoundException extends BusinessRuleException {

    public ModuleNotFoundException(String message) {
        super(message, "MODULE_NOT_FOUND");
    }
}
