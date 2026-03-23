package com.reviewflow.exception;

public class AvatarInvalidTypeException extends RuntimeException {

    private final String code;

    public AvatarInvalidTypeException(String message) {
        super(message);
        this.code = "AVATAR_INVALID_TYPE";
    }

    public String getCode() {
        return code;
    }
}
