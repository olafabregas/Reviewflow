package com.reviewflow.exception;

public class BlockedFileTypeException extends RuntimeException {
    private final String code;

    public BlockedFileTypeException(String extension) {
        super("File type ." + extension + " is not permitted on this platform");
        this.code = "FILE_TYPE_BLOCKED";
    }

    public String getCode() {
        return code;
    }
}
