package com.reviewflow.exception;

public class AvatarUploadFailedException extends RuntimeException {

    private final String code;

    public AvatarUploadFailedException(String message, Throwable cause) {
        super(message, cause);
        this.code = "AVATAR_UPLOAD_FAILED";
    }

    public String getCode() {
        return code;
    }
}
