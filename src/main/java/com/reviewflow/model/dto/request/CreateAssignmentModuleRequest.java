package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAssignmentModuleRequest {

    @Schema(description = "Module name", example = "Week 3 - Database Design", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 100)
    private String name;

    @Schema(description = "Display order within course", example = "3", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(0)
    private Integer displayOrder;
}
