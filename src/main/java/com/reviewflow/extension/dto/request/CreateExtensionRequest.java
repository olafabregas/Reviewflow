package com.reviewflow.extension.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExtensionRequest {

  @NotBlank(message = "reason is required")
  private String reason;

  @NotNull(message = "requestedDueAt is required")
  @Future(message = "requestedDueAt must be in the future")
  private Instant requestedDueAt;
}
