package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddRubricRequest {
  @Schema(
      description = "Name of the rubric criterion",
      example = "Code Quality",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank
  private String name;

  private String description;
  @NotNull private Integer maxScore;
  private Integer displayOrder = 0;
}
