package com.reviewflow.course.exception;

import com.reviewflow.shared.exception.AccessDeniedException;

public class CourseNotOwnedException extends AccessDeniedException {

  public CourseNotOwnedException(String message) {
    super(message);
  }

  public CourseNotOwnedException() {
    super("You do not have permission to create announcements for this course");
  }

  public String getErrorCode() {
    return "COURSE_NOT_OWNED";
  }
}
