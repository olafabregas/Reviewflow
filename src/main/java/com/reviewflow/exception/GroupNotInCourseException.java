package com.reviewflow.exception;

public class GroupNotInCourseException extends ValidationException {

    public GroupNotInCourseException(String message) {
        super(message, "GROUP_NOT_IN_COURSE");
    }
}