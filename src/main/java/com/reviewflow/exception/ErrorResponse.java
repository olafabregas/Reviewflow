package com.reviewflow.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    boolean success = false;

    ErrorDetail error;

    Instant timestamp;

    @Value
    @Builder
    public static class ErrorDetail {
        String code;
        String message;
    }
}
