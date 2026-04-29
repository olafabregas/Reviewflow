package com.reviewflow.shared.exception;

public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }

  public ResourceNotFoundException(String resourceName, Object identifier) {
    super(resourceName + " not found for identifier: " + identifier);
  }
}