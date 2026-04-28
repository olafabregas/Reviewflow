package com.reviewflow.exception;

/**
 * CourseNotOwnedException — thrown when instructor attempts to create announcement for a course
 * they don't teach. HTTP 403 Forbidden.
 */
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
