package com.reviewflow.system.exception;

public class ForceLogoutBlockedException extends RuntimeException {

  public ForceLogoutBlockedException() {
    super("SYSTEM_ADMIN cannot force-logout themselves");
  }

  public ForceLogoutBlockedException(String message) {
    super(message);
  }
}
