package com.reviewflow.shared.api;

public record ValidationFieldError(
    String field, String message, String rejectedValue) {}
