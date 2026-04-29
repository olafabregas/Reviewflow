package com.reviewflow.course.exception;

import com.reviewflow.shared.exception.ValidationException;

public class GroupNotInCourseException extends ValidationException {

  public GroupNotInCourseException(String message) {
    super(message, "GROUP_NOT_IN_COURSE");
  }
}
