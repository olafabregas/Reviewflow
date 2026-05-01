package com.reviewflow.shared.exception;

public class InvalidHashException extends RuntimeException {
  public InvalidHashException(String hash) {
    super("Invalid ID format: " + hash);
  }
}
