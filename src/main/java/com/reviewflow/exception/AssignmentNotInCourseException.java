package com.reviewflow.exception;

public class AssignmentNotInCourseException extends BusinessRuleException {

  public AssignmentNotInCourseException() {
    super("Assignment does not belong to the specified course", "ASSIGNMENT_NOT_IN_COURSE");
  }
}
