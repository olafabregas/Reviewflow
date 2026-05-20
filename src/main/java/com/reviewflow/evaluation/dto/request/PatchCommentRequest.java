package com.reviewflow.evaluation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PatchCommentRequest {
  @Schema(
      description = "Overall comment for the evaluation",
      example = "Great work overall, but needs improvement in error handling.",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @Size(max = 2000)
  private String overallComment;
}
