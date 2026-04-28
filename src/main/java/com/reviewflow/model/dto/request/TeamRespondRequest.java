package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TeamRespondRequest {
  @Schema(
      description = "Whether to accept the team invitation",
      example = "true",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull
  private Boolean accept;
}
