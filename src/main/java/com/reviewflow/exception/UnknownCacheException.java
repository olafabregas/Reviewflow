package com.reviewflow.exception;

public class UnknownCacheException extends RuntimeException {

    public UnknownCacheException(String cacheName) {
        super("Unknown cache name: " + cacheName);
    }

    public UnknownCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
