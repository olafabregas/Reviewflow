package com.reviewflow.exception;

public class CacheEvictionThrottledException extends RuntimeException {

    public CacheEvictionThrottledException(String message) {
        super(message);
    }
}
