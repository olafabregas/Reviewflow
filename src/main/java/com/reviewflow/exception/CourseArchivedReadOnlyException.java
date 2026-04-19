package com.reviewflow.exception;

public class CourseArchivedReadOnlyException extends BusinessRuleException {

    public CourseArchivedReadOnlyException(String message) {
        super(message, "COURSE_ARCHIVED_READ_ONLY");
    }
}
