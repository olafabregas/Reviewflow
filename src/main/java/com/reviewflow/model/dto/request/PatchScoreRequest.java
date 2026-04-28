package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class PatchScoreRequest {
  @Schema(
      description = "Score for the rubric criterion",
      example = "8.5",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull
  private BigDecimal score;

  private String comment;
}
