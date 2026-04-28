package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class PatchCommentRequest {
  @Schema(
      description = "Overall comment for the evaluation",
      example = "Great work overall, but needs improvement in error handling.",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private String overallComment;
}
