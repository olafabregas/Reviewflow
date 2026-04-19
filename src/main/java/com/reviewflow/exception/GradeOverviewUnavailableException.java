package com.reviewflow.exception;

public class GradeOverviewUnavailableException extends BusinessRuleException {

    public GradeOverviewUnavailableException(String message) {
        super(message, "GRADE_OVERVIEW_UNAVAILABLE");
    }
}
