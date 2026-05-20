package com.reviewflow.assignment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MoveAssignmentGroupRequest {

  @Schema(
      description = "Target assignment group hashid",
      example = "grpTarget123",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank
  private String groupId;
}
