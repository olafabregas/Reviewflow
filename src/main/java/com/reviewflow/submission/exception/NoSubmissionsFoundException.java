package com.reviewflow.submission.exception;

public class NoSubmissionsFoundException extends RuntimeException {

  public NoSubmissionsFoundException() {
    super("No submissions found for this assignment");
  }
}
