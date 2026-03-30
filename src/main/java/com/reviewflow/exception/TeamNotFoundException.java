package com.reviewflow.exception;

public class TeamNotFoundException extends RuntimeException {

    public TeamNotFoundException(String teamId) {
        super("Team not found: " + teamId);
    }

    public TeamNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
