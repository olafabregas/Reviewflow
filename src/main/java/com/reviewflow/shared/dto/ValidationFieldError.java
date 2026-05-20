package com.reviewflow.shared.dto;

public record ValidationFieldError(String field, String message, String rejectedValue) {}
