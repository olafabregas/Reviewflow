package com.reviewflow.shared.exception;

public class StorageNotFoundException extends RuntimeException {

  public StorageNotFoundException(String message) {
    super(message);
  }
}
