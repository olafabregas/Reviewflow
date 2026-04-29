package com.reviewflow.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class GradeOverviewUnavailableException extends BusinessRuleException {

  public GradeOverviewUnavailableException(String message) {
    super(message, "GRADE_OVERVIEW_UNAVAILABLE");
  }
}
