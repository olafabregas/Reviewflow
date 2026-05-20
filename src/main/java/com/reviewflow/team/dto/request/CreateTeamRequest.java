package com.reviewflow.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTeamRequest {

  @Schema(description = "Team name", example = "Team Alpha", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank
  @Size(max = 100)
  private String name;
}
