package com.reviewflow.exception;

public class GroupNotEmptyException extends BusinessRuleException {

    public GroupNotEmptyException(String message) {
        super(message, "GROUP_NOT_EMPTY");
    }
}