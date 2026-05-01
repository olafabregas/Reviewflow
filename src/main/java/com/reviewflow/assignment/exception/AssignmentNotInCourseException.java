package com.reviewflow.assignment.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class AssignmentNotInCourseException extends BusinessRuleException {

  public AssignmentNotInCourseException() {
    super("Assignment does not belong to the specified course", "ASSIGNMENT_NOT_IN_COURSE");
  }
}
