package com.reviewflow.exception;

public class SystemAdminLimitExceededException extends RuntimeException {

    public SystemAdminLimitExceededException() {
        super("Maximum 5 SYSTEM_ADMIN accounts reached");
    }

    public SystemAdminLimitExceededException(String message) {
        super(message);
    }
}
