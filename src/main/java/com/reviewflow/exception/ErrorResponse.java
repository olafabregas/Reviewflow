package com.reviewflow.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

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
