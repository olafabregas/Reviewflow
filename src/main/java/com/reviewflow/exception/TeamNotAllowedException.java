package com.reviewflow.exception;

public class TeamNotAllowedException extends BusinessRuleException {

    public TeamNotAllowedException(String message) {
        super(message, "TEAM_NOT_ALLOWED");
    }
}
