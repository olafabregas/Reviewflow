package com.reviewflow.exception;

public class ModuleNotInCourseException extends ValidationException {

    public ModuleNotInCourseException(String message) {
        super(message, "MODULE_NOT_IN_COURSE");
    }
}
