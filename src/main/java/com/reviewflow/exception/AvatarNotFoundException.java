package com.reviewflow.exception;

public class AvatarNotFoundException extends RuntimeException {

    private final String code;

    public AvatarNotFoundException(String message) {
        super(message);
        this.code = "AVATAR_NOT_FOUND";
    }

    public String getCode() {
        return code;
    }
}
