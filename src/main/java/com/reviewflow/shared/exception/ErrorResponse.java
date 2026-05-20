package com.reviewflow.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.reviewflow.shared.dto.ValidationFieldError;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ErrorDetail {
    String code;
    String message;
    Map<String, Object> details;
    List<ValidationFieldError> fields;
  }
}
