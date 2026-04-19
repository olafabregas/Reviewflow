package com.reviewflow.exception;

public class ScoreNotPublishedException extends BusinessRuleException {

    public ScoreNotPublishedException(String message) {
        super(message, "SCORE_NOT_PUBLISHED");
    }
}
