package com.reviewflow.exception;

public class ScoresExistException extends BusinessRuleException {

    public ScoresExistException(String message) {
        super(message, "SCORES_EXIST");
    }
}
