package com.reviewflow.exception;

public class NotInTeamException extends BusinessRuleException {

    public NotInTeamException(String message) {
        super(message, "NOT_IN_TEAM");
    }
}
