package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReopenInstructorScoreRequest {

    @Schema(description = "Reason for reopening a published score", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String reason;
}
