package com.reviewflow.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  boolean success;
  T data;
  ErrorDetail error;
  Instant timestamp;

  // Success response
  public static <T> ApiResponse<T> ok(T data) {
    return ApiResponse.<T>builder().success(true).data(data).timestamp(Instant.now()).build();
  }

  // Error response
  public static <T> ApiResponse<T> error(String code, String message) {
    return ApiResponse.<T>builder()
        .success(false)
        .error(new ErrorDetail(code, message))
        .timestamp(Instant.now())
        .build();
  }

  // Inner class for error details
  @Value
  public static class ErrorDetail {
    String code;
    String message;
  }
}
