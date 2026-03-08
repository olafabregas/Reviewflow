package com.reviewflow.exception;

public class InvalidMimeTypeException extends RuntimeException {
    private final String code;

    public InvalidMimeTypeException(String message) {
        super(message);
        this.code = "INVALID_MIME_TYPE";
    }

    public InvalidMimeTypeException(String detectedMime, String expectedExtension) {
        super("File content does not match its extension");
        this.code = "INVALID_MIME_TYPE";
    }

    public String getCode() {
        return code;
    }
}
