package com.reviewflow.exception;

public class AvatarTooLargeException extends RuntimeException {

    private final String code;

    public AvatarTooLargeException(String message) {
        super(message);
        this.code = "AVATAR_TOO_LARGE";
    }

    public String getCode() {
        return code;
    }
}
