package com.reviewflow.exception;

public class EvaluationNotFoundException extends RuntimeException {

    public EvaluationNotFoundException(String evaluationId) {
        super("Evaluation not found: " + evaluationId);
    }

    public EvaluationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
