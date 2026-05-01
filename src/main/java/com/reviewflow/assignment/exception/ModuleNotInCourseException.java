package com.reviewflow.assignment.exception;

import com.reviewflow.shared.exception.ValidationException;

public class ModuleNotInCourseException extends ValidationException {

  public ModuleNotInCourseException(String message) {
    super(message, "MODULE_NOT_IN_COURSE");
  }
}
