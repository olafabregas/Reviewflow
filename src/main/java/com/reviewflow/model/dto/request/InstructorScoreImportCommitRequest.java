package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InstructorScoreImportCommitRequest {

    @Schema(description = "Dry-run import id returned by preview endpoint", example = "imp-uuid-123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String importId;
}
