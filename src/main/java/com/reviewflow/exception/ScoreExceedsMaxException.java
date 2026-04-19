package com.reviewflow.exception;

public class ScoreExceedsMaxException extends ValidationException {

    public ScoreExceedsMaxException(String message) {
        super(message, "SCORE_EXCEEDS_MAX");
    }
}
