package com.reviewflow.exception;

public class InvalidFileTypeException extends RuntimeException {
    private final String code;

    public InvalidFileTypeException(String extension) {
        super("File type ." + extension + " is not allowed. See documentation for accepted formats");
        this.code = "INVALID_FILE_TYPE";
    }

    public String getCode() {
        return code;
    }
}
