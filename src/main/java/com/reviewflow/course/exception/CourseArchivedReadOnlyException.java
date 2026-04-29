package com.reviewflow.course.exception;

import com.reviewflow.shared.exception.BusinessRuleException;

public class CourseArchivedReadOnlyException extends BusinessRuleException {

  public CourseArchivedReadOnlyException(String message) {
    super(message, "COURSE_ARCHIVED_READ_ONLY");
  }
}
